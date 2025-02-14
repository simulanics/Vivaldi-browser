// Copyright 2017 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_CHROME_BROWSER_UI_OMNIBOX_POPUP_OMNIBOX_POPUP_MEDIATOR_H_
#define IOS_CHROME_BROWSER_UI_OMNIBOX_POPUP_OMNIBOX_POPUP_MEDIATOR_H_

#import <UIKit/UIKit.h>

#include <memory>

#include "components/omnibox/browser/autocomplete_result.h"
#import "ios/chrome/browser/ui/omnibox/popup/autocomplete_result_consumer.h"
#import "ios/chrome/browser/ui/omnibox/popup/favicon_retriever.h"
#import "ios/chrome/browser/ui/omnibox/popup/image_retriever.h"
#include "ui/base/window_open_disposition.h"

@protocol ApplicationCommands;
@protocol BrowserCommands;
@class DefaultBrowserPromoNonModalScheduler;
@class OmniboxPedalAnnotator;
@class OmniboxPopupPresenter;
@class PopupModel;
class FaviconLoader;
class WebStateList;

namespace image_fetcher {
class ImageDataFetcher;
}  // namespace

class OmniboxPopupMediatorDelegate {
 public:
  virtual bool IsStarredMatch(const AutocompleteMatch& match) const = 0;
  virtual void OnMatchSelected(const AutocompleteMatch& match,
                               size_t row,
                               WindowOpenDisposition disposition) = 0;
  virtual void OnMatchSelectedForAppending(const AutocompleteMatch& match) = 0;
  virtual void OnMatchSelectedForDeletion(const AutocompleteMatch& match) = 0;
  virtual void OnScroll() = 0;
};

@interface OmniboxPopupMediator : NSObject <AutocompleteResultConsumerDelegate,
                                            AutocompleteResultDataSource,
                                            ImageRetriever,
                                            FaviconRetriever>

@property(nonatomic, readonly, assign) FaviconLoader* faviconLoader;

// Whether the mediator has results to show.
@property(nonatomic, assign) BOOL hasResults;

// Sets the semantic content attribute of the popup content.
- (void)setSemanticContentAttribute:
    (UISemanticContentAttribute)semanticContentAttribute;

@property(nonatomic, weak) id<BrowserCommands> dispatcher;
@property(nonatomic, weak) id<AutocompleteResultConsumer> consumer;
// Scheduler to notify about events happening in this popup.
@property(nonatomic, weak) DefaultBrowserPromoNonModalScheduler* promoScheduler;
@property(nonatomic, assign, getter=isIncognito) BOOL incognito;
// Whether the popup is open.
@property(nonatomic, assign, getter=isOpen) BOOL open;
// Presenter for the popup, handling the positioning and the presentation
// animations.
@property(nonatomic, strong) OmniboxPopupPresenter* presenter;
// The web state list this mediator is handling.
@property(nonatomic, assign) WebStateList* webStateList;
// Whether the default search engine is Google impacts which icon is used in
// some cases
@property(nonatomic, assign) BOOL defaultSearchEngineIsGoogle;
// The model for this mediator, if one exists.
@property(nonatomic, weak) PopupModel* model;
// The annotator to create pedals for ths mediator.
@property(nonatomic) OmniboxPedalAnnotator* pedalAnnotator;

// Designated initializer. Takes ownership of `imageFetcher`.
- (instancetype)initWithFetcher:
                    (std::unique_ptr<image_fetcher::ImageDataFetcher>)
                        imageFetcher
                  faviconLoader:(FaviconLoader*)faviconLoader
                       delegate:(OmniboxPopupMediatorDelegate*)delegate;

- (void)updateMatches:(const AutocompleteResult&)result;

// Sets the text alignment of the popup content.
- (void)setTextAlignment:(NSTextAlignment)alignment;

// Updates the popup with the `results`.
- (void)updateWithResults:(const AutocompleteResult&)results;

@end

#endif  // IOS_CHROME_BROWSER_UI_OMNIBOX_POPUP_OMNIBOX_POPUP_MEDIATOR_H_
