// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_CHROME_BROWSER_UI_OMNIBOX_POPUP_OMNIBOX_POPUP_VIEW_CONTROLLER_H_
#define IOS_CHROME_BROWSER_UI_OMNIBOX_POPUP_OMNIBOX_POPUP_VIEW_CONTROLLER_H_

#import <UIKit/UIKit.h>

#import "ios/chrome/browser/ui/commands/omnibox_suggestion_commands.h"
#import "ios/chrome/browser/ui/omnibox/omnibox_keyboard_delegate.h"
#import "ios/chrome/browser/ui/omnibox/omnibox_text_change_delegate.h"
#import "ios/chrome/browser/ui/omnibox/popup/autocomplete_result_consumer.h"
#import "ios/chrome/browser/ui/omnibox/popup/content_providing.h"

@protocol CarouselItemMenuProvider;
@protocol FaviconRetriever;
class LargeIconCache;
namespace favicon {
class LargeIconService;
}
@protocol ImageRetriever;
@protocol PopupMatchPreviewDelegate;

// View controller used to display a list of omnibox autocomplete matches in the
// omnibox popup.
// It implements up/down arrow handling to highlight autocomplete results.
// Ideally, that should be implemented as key commands in this view controller,
// but UITextField has standard handlers for up/down arrows, so when the omnibox
// is the first responder, this view controller cannot receive these events.
// Hence the delegation.
@interface OmniboxPopupViewController
    : UIViewController <AutocompleteResultConsumer,
                        ContentProviding,
                        OmniboxKeyboardDelegate,
                        OmniboxReturnDelegate,
                        OmniboxSuggestionCommands,
                        UIScrollViewDelegate>

@property(nonatomic, assign) BOOL incognito;
@property(nonatomic, weak) id<AutocompleteResultConsumerDelegate> delegate;
@property(nonatomic, weak) id<AutocompleteResultDataSource> dataSource;
@property(nonatomic, weak) id<OmniboxReturnDelegate> acceptReturnDelegate;
@property(nonatomic, weak) id<PopupMatchPreviewDelegate> matchPreviewDelegate;
@property(nonatomic, weak) id<ImageRetriever> imageRetriever;
@property(nonatomic, weak) id<FaviconRetriever> faviconRetriever;
@property(nonatomic, assign) favicon::LargeIconService* largeIconService;
@property(nonatomic, assign) LargeIconCache* largeIconCache;
@property(nonatomic, weak) id<CarouselItemMenuProvider> carouselMenuProvider;

@property(nonatomic, strong)
    NSArray<id<AutocompleteSuggestionGroup>>* currentResult;

- (instancetype)init NS_DESIGNATED_INITIALIZER;

- (instancetype)initWithCoder:(NSCoder*)aDecoder NS_UNAVAILABLE;
- (instancetype)initWithNibName:(NSString*)nibNameOrNil
                         bundle:(NSBundle*)nibBundleOrNil NS_UNAVAILABLE;

@end

#endif  // IOS_CHROME_BROWSER_UI_OMNIBOX_POPUP_OMNIBOX_POPUP_VIEW_CONTROLLER_H_
