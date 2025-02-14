// Copyright 2017 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/toolbar/buttons/toolbar_button_factory.h"

#import "components/strings/grit/components_strings.h"
#import "ios/chrome/browser/ui/icons/chrome_symbol.h"
#import "ios/chrome/browser/ui/toolbar/buttons/toolbar_button.h"
#import "ios/chrome/browser/ui/toolbar/buttons/toolbar_button_actions_handler.h"
#import "ios/chrome/browser/ui/toolbar/buttons/toolbar_button_visibility_configuration.h"
#import "ios/chrome/browser/ui/toolbar/buttons/toolbar_configuration.h"
#import "ios/chrome/browser/ui/toolbar/buttons/toolbar_new_tab_button.h"
#import "ios/chrome/browser/ui/toolbar/buttons/toolbar_tab_grid_button.h"
#import "ios/chrome/browser/ui/toolbar/buttons/toolbar_tools_menu_button.h"
#import "ios/chrome/browser/ui/toolbar/public/toolbar_constants.h"
#import "ios/chrome/browser/ui/util/rtl_geometry.h"
#import "ios/chrome/browser/ui/util/uikit_ui_util.h"
#import "ios/chrome/common/ui/colors/semantic_color_names.h"
#import "ios/chrome/common/ui/util/constraints_ui_util.h"
#import "ios/chrome/grit/ios_strings.h"
#import "ios/chrome/grit/ios_theme_resources.h"
#import "ui/base/l10n/l10n_util.h"

// Vivaldi
#include "app/vivaldi_apptools.h"
#import "ios/chrome/browser/ui/tab_strip/vivaldi_tab_strip_constants.h"

using vivaldi::IsVivaldiRunning;
// End Vivaldi

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {

// The size of the symbol image.
const CGFloat kSymbolToolbarPointSize = 24;

// Specific symbols used in the toolbar.
NSString* const kToolbarArrowBackwardSymbol = @"arrow.backward";
NSString* const kToolbarArrowForwardSymbol = @"arrow.forward";

}  // namespace

@implementation ToolbarButtonFactory

- (instancetype)initWithStyle:(ToolbarStyle)style {
  self = [super init];
  if (self) {
    _style = style;
    _toolbarConfiguration = [[ToolbarConfiguration alloc] initWithStyle:style];
  }
  return self;
}

#pragma mark - Buttons

- (ToolbarButton*)backButton {
  UIImage* backImage;
  backImage = UseSymbols()
                  ? DefaultSymbolWithPointSize(kToolbarArrowBackwardSymbol,
                                               kSymbolToolbarPointSize)
                  : [UIImage imageNamed:@"toolbar_back"];
  ToolbarButton* backButton = [ToolbarButton
      toolbarButtonWithImage:[backImage
                                 imageFlippedForRightToLeftLayoutDirection]];
  [self configureButton:backButton width:kAdaptiveToolbarButtonWidth];
  backButton.accessibilityLabel = l10n_util::GetNSString(IDS_ACCNAME_BACK);
  [backButton addTarget:self.actionHandler
                 action:@selector(backAction)
       forControlEvents:UIControlEventTouchUpInside];
  backButton.visibilityMask = self.visibilityConfiguration.backButtonVisibility;
  return backButton;
}

// Returns a forward button without visibility mask configured.
- (ToolbarButton*)forwardButton {
  UIImage* forwardImage =
      UseSymbols() ? DefaultSymbolWithPointSize(kToolbarArrowForwardSymbol,
                                                kSymbolToolbarPointSize)
                   : [UIImage imageNamed:@"toolbar_forward"];
  ToolbarButton* forwardButton = [ToolbarButton
      toolbarButtonWithImage:[forwardImage
                                 imageFlippedForRightToLeftLayoutDirection]];
  [self configureButton:forwardButton width:kAdaptiveToolbarButtonWidth];
  forwardButton.visibilityMask =
      self.visibilityConfiguration.forwardButtonVisibility;
  forwardButton.accessibilityLabel =
      l10n_util::GetNSString(IDS_ACCNAME_FORWARD);
  [forwardButton addTarget:self.actionHandler
                    action:@selector(forwardAction)
          forControlEvents:UIControlEventTouchUpInside];
  return forwardButton;
}

- (ToolbarTabGridButton*)tabGridButton {
  UIImage* tabGridImage =
      UseSymbols() ? CustomSymbolWithPointSize(kSquareNumberSymbol,
                                               kSymbolToolbarPointSize)
                   : tabGridImage = [UIImage imageNamed:@"toolbar_switcher"];
  ToolbarTabGridButton* tabGridButton =
      [ToolbarTabGridButton toolbarButtonWithImage:tabGridImage];
  [self configureButton:tabGridButton width:kAdaptiveToolbarButtonWidth];
  SetA11yLabelAndUiAutomationName(tabGridButton, IDS_IOS_TOOLBAR_SHOW_TABS,
                                  kToolbarStackButtonIdentifier);
  [tabGridButton addTarget:self.actionHandler
                    action:@selector(tabGridTouchDown)
          forControlEvents:UIControlEventTouchDown];
  [tabGridButton addTarget:self.actionHandler
                    action:@selector(tabGridTouchUp)
          forControlEvents:UIControlEventTouchUpInside];
  tabGridButton.visibilityMask =
      self.visibilityConfiguration.tabGridButtonVisibility;
  return tabGridButton;
}

- (ToolbarButton*)toolsMenuButton {
  ToolbarButton* toolsMenuButton;
  if (UseSymbols()) {
    toolsMenuButton = [ToolbarButton
        toolbarButtonWithImage:DefaultSymbolWithPointSize(
                                   kMenuSymbol, kSymbolToolbarPointSize)];
  } else {
    toolsMenuButton = [[ToolbarToolsMenuButton alloc] initWithFrame:CGRectZero];
  }

  if (IsVivaldiRunning()) {
    UIImage* menuImage = [UIImage imageNamed:@"toolbar_menu"];
    toolsMenuButton = [ToolbarToolsMenuButton toolbarButtonWithImage:menuImage];
  } // End Vivaldi

  SetA11yLabelAndUiAutomationName(toolsMenuButton, IDS_IOS_TOOLBAR_SETTINGS,
                                  kToolbarToolsMenuButtonIdentifier);
  [self configureButton:toolsMenuButton width:kAdaptiveToolbarButtonWidth];
  [toolsMenuButton.heightAnchor
      constraintEqualToConstant:kAdaptiveToolbarButtonWidth]
      .active = YES;
  [toolsMenuButton addTarget:self.actionHandler
                      action:@selector(toolsMenuAction)
            forControlEvents:UIControlEventTouchUpInside];
  toolsMenuButton.visibilityMask =
      self.visibilityConfiguration.toolsMenuButtonVisibility;
  return toolsMenuButton;
}

- (ToolbarButton*)shareButton {
  UIImage* shareImage =
      UseSymbols()
          ? DefaultSymbolWithPointSize(kShareSymbol, kSymbolToolbarPointSize)
          : [UIImage imageNamed:@"toolbar_share"];
  ToolbarButton* shareButton =
      [ToolbarButton toolbarButtonWithImage:shareImage];
  [self configureButton:shareButton width:kAdaptiveToolbarButtonWidth];
  SetA11yLabelAndUiAutomationName(shareButton, IDS_IOS_TOOLS_MENU_SHARE,
                                  kToolbarShareButtonIdentifier);
  shareButton.titleLabel.text = @"Share";
  [shareButton addTarget:self.actionHandler
                  action:@selector(shareAction)
        forControlEvents:UIControlEventTouchUpInside];
  shareButton.visibilityMask =
      self.visibilityConfiguration.shareButtonVisibility;
  return shareButton;
}

- (ToolbarButton*)reloadButton {
  UIImage* reloadImage =
      UseSymbols() ? CustomSymbolWithPointSize(kArrowClockWiseSymbol,
                                               kSymbolToolbarPointSize)
                   : [UIImage imageNamed:@"toolbar_reload"];
  ToolbarButton* reloadButton = [ToolbarButton
      toolbarButtonWithImage:[reloadImage
                                 imageFlippedForRightToLeftLayoutDirection]];
  [self configureButton:reloadButton width:kAdaptiveToolbarButtonWidth];
  reloadButton.accessibilityLabel =
      l10n_util::GetNSString(IDS_IOS_ACCNAME_RELOAD);
  [reloadButton addTarget:self.actionHandler
                   action:@selector(reloadAction)
         forControlEvents:UIControlEventTouchUpInside];
  reloadButton.visibilityMask =
      self.visibilityConfiguration.reloadButtonVisibility;
  return reloadButton;
}

- (ToolbarButton*)stopButton {
  UIImage* stopImage = UseSymbols() ? DefaultSymbolWithPointSize(
                                          kXMarkSymbol, kSymbolToolbarPointSize)
                                    : [UIImage imageNamed:@"toolbar_stop"];
  ToolbarButton* stopButton = [ToolbarButton toolbarButtonWithImage:stopImage];
  [self configureButton:stopButton width:kAdaptiveToolbarButtonWidth];
  stopButton.accessibilityLabel = l10n_util::GetNSString(IDS_IOS_ACCNAME_STOP);
  [stopButton addTarget:self.actionHandler
                 action:@selector(stopAction)
       forControlEvents:UIControlEventTouchUpInside];
  stopButton.visibilityMask = self.visibilityConfiguration.stopButtonVisibility;
  return stopButton;
}

- (ToolbarButton*)openNewTabButton {
  UIImage* newTabImage =
      UseSymbols()
          ? DefaultSymbolWithPointSize(kPlusSymbol, kSymbolToolbarPointSize)
          : [UIImage imageNamed:@"toolbar_new_tab_page"];
  ToolbarNewTabButton* newTabButton =
      [ToolbarNewTabButton toolbarButtonWithImage:newTabImage];

  [newTabButton addTarget:self.actionHandler
                   action:@selector(newTabAction:)
         forControlEvents:UIControlEventTouchUpInside];
  BOOL isIncognito = self.style == INCOGNITO;

  [self configureButton:newTabButton width:kAdaptiveToolbarButtonWidth];

  newTabButton.accessibilityLabel =
      l10n_util::GetNSString(isIncognito ? IDS_IOS_TOOLS_MENU_NEW_INCOGNITO_TAB
                                         : IDS_IOS_TOOLS_MENU_NEW_TAB);

  newTabButton.accessibilityIdentifier = kToolbarNewTabButtonIdentifier;

  newTabButton.visibilityMask =
      self.visibilityConfiguration.searchButtonVisibility;
  return newTabButton;
}

- (UIButton*)cancelButton {
  UIButton* cancelButton = [UIButton buttonWithType:UIButtonTypeSystem];
  cancelButton.titleLabel.font = [UIFont systemFontOfSize:kLocationBarFontSize];
  cancelButton.tintColor = [UIColor colorNamed:kBlueColor];
  [cancelButton setTitle:l10n_util::GetNSString(IDS_CANCEL)
                forState:UIControlStateNormal];
  [cancelButton setContentHuggingPriority:UILayoutPriorityRequired
                                  forAxis:UILayoutConstraintAxisHorizontal];
  [cancelButton
      setContentCompressionResistancePriority:UILayoutPriorityRequired
                                      forAxis:UILayoutConstraintAxisHorizontal];
  cancelButton.contentEdgeInsets = UIEdgeInsetsMake(
      0, kCancelButtonHorizontalInset, 0, kCancelButtonHorizontalInset);
  cancelButton.hidden = YES;
  [cancelButton addTarget:self.actionHandler
                   action:@selector(cancelOmniboxFocusAction)
         forControlEvents:UIControlEventTouchUpInside];
  cancelButton.accessibilityIdentifier =
      kToolbarCancelOmniboxEditButtonIdentifier;
  return cancelButton;
}

// Vivaldi
- (ToolbarButton*)panelButton {
  UIImage* panelImage = [UIImage imageNamed:vPrimaryToolbarPanelButton];
  ToolbarButton* panelButton = [ToolbarButton
      toolbarButtonWithImage:[panelImage
                                 imageFlippedForRightToLeftLayoutDirection]];
  [self configureButton:panelButton width:kAdaptiveToolbarButtonWidth];
  // Todo: prio@vivaldi.com - Update accessibility label
  panelButton.accessibilityLabel = l10n_util::GetNSString(IDS_ACCNAME_BACK);
  [panelButton addTarget:self.actionHandler
                 action:@selector(panelAction)
       forControlEvents:UIControlEventTouchUpInside];
  panelButton.visibilityMask = self.visibilityConfiguration.backButtonVisibility;
  return panelButton;
} // End Vivaldi

#pragma mark - Helpers

// Sets the `button` width to `width` with a priority of
// UILayoutPriorityRequired - 1. If the priority is `UILayoutPriorityRequired`,
// there is a conflict when the buttons are hidden as the stack view is setting
// their width to 0. Setting the priority to UILayoutPriorityDefaultHigh doesn't
// work as they would have a lower priority than other elements.
- (void)configureButton:(ToolbarButton*)button width:(CGFloat)width {
  NSLayoutConstraint* constraint =
      [button.widthAnchor constraintEqualToConstant:width];
  constraint.priority = UILayoutPriorityRequired - 1;
  constraint.active = YES;
  button.toolbarConfiguration = self.toolbarConfiguration;
  button.exclusiveTouch = YES;
  button.pointerInteractionEnabled = YES;
  button.pointerStyleProvider =
      ^UIPointerStyle*(UIButton* uiButton, UIPointerEffect* proposedEffect,
                       UIPointerShape* proposedShape) {
    // This gets rid of a thin border on a spotlighted bookmarks button.
    // This is applied to all toolbar buttons for consistency.
    CGRect rect = CGRectInset(uiButton.frame, 1, 1);
    UIPointerShape* shape = [UIPointerShape shapeWithRoundedRect:rect];
    return [UIPointerStyle styleWithEffect:proposedEffect shape:shape];
  };
}

@end
