// Copyright 2017 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/activity_services/activity_service_coordinator.h"

#import "components/bookmarks/browser/bookmark_model.h"
#import "ios/chrome/browser/bookmarks/bookmark_model_factory.h"
#import "ios/chrome/browser/browser_state/chrome_browser_state.h"
#import "ios/chrome/browser/main/browser.h"
#import "ios/chrome/browser/ui/activity_services/activity_params.h"
#import "ios/chrome/browser/ui/activity_services/activity_service_mediator.h"
#import "ios/chrome/browser/ui/activity_services/canonical_url_retriever.h"
#import "ios/chrome/browser/ui/activity_services/data/chrome_activity_file_source.h"
#import "ios/chrome/browser/ui/activity_services/data/chrome_activity_image_source.h"
#import "ios/chrome/browser/ui/activity_services/data/chrome_activity_item_source.h"
#import "ios/chrome/browser/ui/activity_services/data/chrome_activity_text_source.h"
#import "ios/chrome/browser/ui/activity_services/data/chrome_activity_url_source.h"
#import "ios/chrome/browser/ui/activity_services/data/share_file_data.h"
#import "ios/chrome/browser/ui/activity_services/data/share_image_data.h"
#import "ios/chrome/browser/ui/activity_services/data/share_to_data.h"
#import "ios/chrome/browser/ui/activity_services/data/share_to_data_builder.h"
#import "ios/chrome/browser/ui/activity_services/requirements/activity_service_positioner.h"
#import "ios/chrome/browser/ui/activity_services/requirements/activity_service_presentation.h"
#import "ios/chrome/browser/ui/commands/bookmarks_commands.h"
#import "ios/chrome/browser/ui/commands/command_dispatcher.h"
#import "ios/chrome/browser/ui/main/default_browser_scene_agent.h"
#import "ios/chrome/browser/ui/main/scene_state_browser_agent.h"
#import "ios/chrome/browser/ui/util/uikit_ui_util.h"
#import "ios/chrome/browser/web/web_navigation_browser_agent.h"
#import "ios/chrome/browser/web_state_list/web_state_list.h"
#import "ios/web/public/web_state.h"
#import "net/base/mac/url_conversions.h"
#import "url/gurl.h"

#import <LinkPresentation/LinkPresentation.h>

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {

// MIME type of PDF.
const char kMimeTypePDF[] = "application/pdf";

}  // namespace

@interface ActivityServiceCoordinator ()

@property(nonatomic, weak)
    id<BrowserCommands, BrowserCoordinatorCommands, FindInPageCommands>
        handler;

@property(nonatomic, strong) ActivityServiceMediator* mediator;

@property(nonatomic, strong) UIActivityViewController* viewController;

// Parameters determining the activity flow and values.
@property(nonatomic, strong) ActivityParams* params;

@end

@implementation ActivityServiceCoordinator

- (instancetype)initWithBaseViewController:(UIViewController*)baseViewController
                                   browser:(Browser*)browser
                                    params:(ActivityParams*)params {
  DCHECK(params);
  if (self = [super initWithBaseViewController:baseViewController
                                       browser:browser]) {
    _params = params;
  }
  return self;
}

#pragma mark - Public methods

- (void)start {
  self.handler = static_cast<
      id<BrowserCommands, BrowserCoordinatorCommands, FindInPageCommands>>(
      self.browser->GetCommandDispatcher());

  ChromeBrowserState* browserState = self.browser->GetBrowserState();
  bookmarks::BookmarkModel* bookmarkModel =
      ios::BookmarkModelFactory::GetForBrowserState(browserState);
  id<BookmarksCommands> bookmarksHandler = HandlerForProtocol(
      self.browser->GetCommandDispatcher(), BookmarksCommands);
  WebNavigationBrowserAgent* agent =
      WebNavigationBrowserAgent::FromBrowser(self.browser);
  self.mediator =
      [[ActivityServiceMediator alloc] initWithHandler:self.handler
                                      bookmarksHandler:bookmarksHandler
                                   qrGenerationHandler:self.scopedHandler
                                           prefService:browserState->GetPrefs()
                                         bookmarkModel:bookmarkModel
                                    baseViewController:self.baseViewController
                                       navigationAgent:agent];

  SceneState* sceneState =
      SceneStateBrowserAgent::FromBrowser(self.browser)->GetSceneState();
  self.mediator.promoScheduler =
      [DefaultBrowserSceneAgent agentFromScene:sceneState].nonModalScheduler;

  [self.mediator shareStartedWithScenario:self.params.scenario];

  // Image item
  if (self.params.image) {
    [self shareImage];
    return;
  }

  if (self.params.filePath) {
    [self shareFile];
    return;
  }

  if (self.params.URLs.count > 0) {
    // If at least one valid URL is found, share the URLs in `_params`.
    for (URLWithTitle* urlWithTitle in self.params.URLs) {
      if (!urlWithTitle.URL.is_empty()) {
        [self shareURLs];
        return;
      }
    }
  }

  // Default to sharing the current page
  [self shareCurrentPage];
}

- (void)stop {
  [self.viewController.presentingViewController
      dismissViewControllerAnimated:YES
                         completion:nil];
  self.viewController = nil;

  self.mediator = nil;
}

#pragma mark - Private Methods

// Sets up the activity ViewController with the given `items` and `activities`.
- (void)shareItems:(NSArray<id<ChromeActivityItemSource>>*)items
        activities:(NSArray*)activities {
  self.viewController =
      [[UIActivityViewController alloc] initWithActivityItems:items
                                        applicationActivities:activities];

  [self.viewController setModalPresentationStyle:UIModalPresentationPopover];

  NSSet* excludedActivityTypes =
      [self.mediator excludedActivityTypesForItems:items];
  [self.viewController
      setExcludedActivityTypes:[excludedActivityTypes allObjects]];

  // Set-up popover positioning (for iPad).
  DCHECK(self.positionProvider);
  if ([self.positionProvider respondsToSelector:@selector(barButtonItem)] &&
      self.positionProvider.barButtonItem) {
    self.viewController.popoverPresentationController.barButtonItem =
        self.positionProvider.barButtonItem;
  } else {
    self.viewController.popoverPresentationController.sourceView =
        self.positionProvider.sourceView;
    self.viewController.popoverPresentationController.sourceRect =
        self.positionProvider.sourceRect;
  }

  // Set completion callback.
  __weak __typeof(self) weakSelf = self;
  [self.viewController setCompletionWithItemsHandler:^(
                           NSString* activityType, BOOL completed,
                           NSArray* returnedItems, NSError* activityError) {
    ActivityServiceCoordinator* strongSelf = weakSelf;
    if (!strongSelf) {
      return;
    }

    // Delegate post-activity processing to the mediator.
    [strongSelf.mediator shareFinishedWithScenario:strongSelf.params.scenario
                                      activityType:activityType
                                         completed:completed];

    // If it is completed by finishing a scenario or if the view been closed by
    // the user without selecting a service.
    BOOL isActivityViewControllerDismissed =
        completed || (!activityType && !completed);
    if (isActivityViewControllerDismissed) {
      // Signal the presentation provider that our scenario is over.
      [strongSelf.presentationProvider activityServiceDidEndPresenting];
    }
  }];

  [self.baseViewController presentViewController:self.viewController
                                        animated:YES
                                      completion:nil];
}

#pragma mark - Private Methods: Current Page

// Fetches the current tab's URL, configures activities and items, and shows
// an activity view.
- (void)shareCurrentPage {
  web::WebState* currentWebState =
      self.browser->GetWebStateList()->GetActiveWebState();

  // In some cases it seems that the share sheet is triggered while no tab is
  // present (probably due to a timing issue).
  if (!currentWebState)
    return;

  // Retrieve the current page's URL.
  __weak __typeof(self) weakSelf = self;
  activity_services::RetrieveCanonicalUrl(currentWebState, ^(const GURL& url) {
    [weakSelf sharePageWithCanonicalURL:url];
  });
}

// Shares the current page using its `canonicalURL`.
- (void)sharePageWithCanonicalURL:(const GURL&)canonicalURL {
  ShareToData* data = activity_services::ShareToDataForWebState(
      self.browser->GetWebStateList()->GetActiveWebState(), canonicalURL);
  if (!data)
    return;

  NSArray<ChromeActivityURLSource*>* items =
      [self.mediator activityItemsForDataItems:@[ data ]];
  NSArray* activities =
      [self.mediator applicationActivitiesForDataItems:@[ data ]];

  [self shareItems:items activities:activities];
}

#pragma mark - Private Methods: Share Image

// Configures activities and items for an image and its title, and shows
// an activity view.
- (void)shareImage {
  ShareImageData* data =
      [[ShareImageData alloc] initWithImage:self.params.image
                                      title:self.params.imageTitle];

  NSArray<ChromeActivityImageSource*>* items =
      [self.mediator activityItemsForImageData:data];
  NSArray* activities = [self.mediator applicationActivitiesForImageData:data];

  [self shareItems:items activities:activities];
}

#pragma mark - Private Methods: Share File

// Configures activities and items, and shows an activity view.
- (void)shareFile {
  web::WebState* currentWebState =
      self.browser->GetWebStateList()->GetActiveWebState();

  // In some cases it seems that the share sheet is triggered while no tab is
  // present (probably due to a timing issue).
  if (!currentWebState)
    return;

  // Retrieve the current page's URL.
  __weak __typeof(self) weakSelf = self;
  activity_services::RetrieveCanonicalUrl(currentWebState, ^(const GURL& url) {
    ShareToData* URLData = activity_services::ShareToDataForWebState(
        weakSelf.browser->GetWebStateList()->GetActiveWebState(), url);

    // As giving a PDF file to the UIActivityViewController will add the "Print"
    // activity from Apple, Chrome's print activity is disabled to avoid
    // duplicate.
    BOOL isPDF = currentWebState->GetContentsMimeType() == kMimeTypePDF;
    if (isPDF) {
      URLData.isPagePrintable = NO;
    }

    ShareFileData* fileData =
        [[ShareFileData alloc] initWithFilePath:self.params.filePath];

    NSArray<ChromeActivityFileSource*>* items =
        [weakSelf.mediator activityItemsForFileData:fileData];
    NSArray* activities =
        [weakSelf.mediator applicationActivitiesForDataItems:@[ URLData ]];
    [weakSelf shareItems:items activities:activities];
  });
}

#pragma mark - Private Methods: Share URL

// Configures activities and items for a URL and its title, and shows
// an activity view. Also adds another activity item for additional text, if
// there is any.
- (void)shareURLs {
  NSMutableArray* dataItems = [[NSMutableArray alloc] init];
  ActivityParams* params = self.params;

  // If only given a single URL, include additionalText in shared payload.
  if (params.URLs.count == 1) {
    URLWithTitle* url = params.URLs[0];
    LPLinkMetadata* metadata = [self linkMetadata:url];
    ShareToData* data = activity_services::ShareToDataForURL(
        url.URL, url.title, params.additionalText, metadata);
    [dataItems addObject:data];
  } else {
    for (URLWithTitle* urlWithTitle in params.URLs) {
      ShareToData* data =
          activity_services::ShareToDataForURLWithTitle(urlWithTitle);
      [dataItems addObject:data];
    }
  }

  NSArray<id<ChromeActivityItemSource>>* items =
      [self.mediator activityItemsForDataItems:dataItems];
  NSArray* activities =
      [self.mediator applicationActivitiesForDataItems:dataItems];

  [self shareItems:items activities:activities];
}

// Returns some basic metadata for the Chrome App's app store link. If we do
// not supply this metadata, UIActivityViewController will only display a
// generic website icon and the hostname when given an app store link.
- (LPLinkMetadata*)linkMetadata:(URLWithTitle*)url {
  if (self.params.scenario != ActivityScenario::ShareChrome) {
    // For non app store links, we will allow UIActivityViewController to choose
    // how to display.
    return nil;
  }

  LPLinkMetadata* metadata = [[LPLinkMetadata alloc] init];
  metadata.originalURL = net::NSURLWithGURL(url.URL);
  metadata.title = url.title;
  metadata.iconProvider = [self appIconProvider];
  return metadata;
}

- (NSItemProvider*)appIconProvider {
  NSDictionary* allIcons =
      [[NSBundle mainBundle] infoDictionary][@"CFBundleIcons"];
  NSDictionary* primaryIcon = allIcons[@"CFBundlePrimaryIcon"];
  NSArray* iconFiles = primaryIcon[@"CFBundleIconFiles"];
  UIImage* iconFile = [UIImage imageNamed:iconFiles.lastObject];
  return [[NSItemProvider alloc] initWithObject:iconFile];
}

@end
