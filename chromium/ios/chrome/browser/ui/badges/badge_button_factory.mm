// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/badges/badge_button_factory.h"

#import <ostream>

#import "base/notreached.h"
#import "components/password_manager/core/common/password_manager_features.h"
#import "ios/chrome/browser/ui/badges/badge_button.h"
#import "ios/chrome/browser/ui/badges/badge_constants.h"
#import "ios/chrome/browser/ui/badges/badge_delegate.h"
#import "ios/chrome/browser/ui/badges/badge_overflow_menu_util.h"
#import "ios/chrome/browser/ui/icons/chrome_symbol.h"
#import "ios/chrome/browser/ui/icons/infobar_icon.h"
#import "ios/chrome/browser/ui/ui_feature_flags.h"
#import "ios/chrome/common/ui/colors/semantic_color_names.h"
#import "ios/chrome/grit/ios_strings.h"
#import "ui/base/l10n/l10n_util.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {
// The identifier for the new popup menu action trigger.
NSString* const kOverflowPopupMenuActionIdentifier =
    @"kOverflowPopupMenuActionIdentifier";

// The size of the incognito symbol image.
const CGFloat kSymbolIncognitoPointSize = 28.;

// The size of the incognito full screen symbol image.
const CGFloat kSymbolIncognitoFullScreenPointSize = 14.;

}  // namespace

@implementation BadgeButtonFactory

- (BadgeButton*)badgeButtonForBadgeType:(BadgeType)badgeType {
  switch (badgeType) {
    case kBadgeTypePasswordSave:
      return [self passwordsSaveBadgeButton];
    case kBadgeTypePasswordUpdate:
      return [self passwordsUpdateBadgeButton];
    case kBadgeTypeSaveCard:
      return [self saveCardBadgeButton];
    case kBadgeTypeTranslate:
      return [self translateBadgeButton];
    case kBadgeTypeIncognito:
      return [self incognitoBadgeButton];
    case kBadgeTypeOverflow:
      return [self overflowBadgeButton];
    case kBadgeTypeSaveAddressProfile:
      return [self saveAddressProfileBadgeButton];
    case kBadgeTypeAddToReadingList:
      return [self readingListBadgeButton];
    case kBadgeTypePermissionsCamera:
      return [self permissionsCameraBadgeButton];
    case kBadgeTypePermissionsMicrophone:
      return [self permissionsMicrophoneBadgeButton];
    case kBadgeTypeNone:
      NOTREACHED() << "A badge should not have kBadgeTypeNone";
      return nil;
  }
}

#pragma mark - Private

// Convenience getter for the URI asset name of the password_key icon, based on
// finch flag enable/disable status
- (NSString*)passwordKeyAssetName {
  return base::FeatureList::IsEnabled(
             password_manager::features::
                 kIOSEnablePasswordManagerBrandingUpdate)
             ? @"password_key"
             : @"legacy_password_key";
}

- (BadgeButton*)passwordsSaveBadgeButton {
  UIImage* image =
      UseSymbols()
          ? CustomSymbolWithPointSize(kPasswordSymbol, kSymbolImagePointSize)
          : [UIImage imageNamed:[self passwordKeyAssetName]];
  BadgeButton* button =
      [self createButtonForType:kBadgeTypePasswordSave
                          image:[image imageWithRenderingMode:
                                           UIImageRenderingModeAlwaysTemplate]];
  [button addTarget:self.delegate
                action:@selector(passwordsBadgeButtonTapped:)
      forControlEvents:UIControlEventTouchUpInside];
  button.accessibilityIdentifier =
      kBadgeButtonSavePasswordAccessibilityIdentifier;
  button.accessibilityLabel =
      l10n_util::GetNSString(IDS_IOS_INFOBAR_BADGES_PASSWORD_HINT);
  return button;
}

- (BadgeButton*)passwordsUpdateBadgeButton {
  UIImage* image =
      UseSymbols()
          ? CustomSymbolWithPointSize(kPasswordSymbol, kSymbolImagePointSize)
          : [UIImage imageNamed:[self passwordKeyAssetName]];
  BadgeButton* button =
      [self createButtonForType:kBadgeTypePasswordUpdate
                          image:[image imageWithRenderingMode:
                                           UIImageRenderingModeAlwaysTemplate]];
  [button addTarget:self.delegate
                action:@selector(passwordsBadgeButtonTapped:)
      forControlEvents:UIControlEventTouchUpInside];
  button.accessibilityIdentifier =
      kBadgeButtonUpdatePasswordAccessibilityIdentifier;
  button.accessibilityLabel =
      l10n_util::GetNSString(IDS_IOS_INFOBAR_BADGES_PASSWORD_HINT);
  return button;
}

- (BadgeButton*)saveCardBadgeButton {
  UIImage* image;
  image = UseSymbols() ? DefaultSymbolWithPointSize(kCreditCardSymbol,
                                                    kSymbolImagePointSize)
                       : [UIImage imageNamed:@"infobar_save_card_icon"];
  BadgeButton* button =
      [self createButtonForType:kBadgeTypeSaveCard
                          image:[image imageWithRenderingMode:
                                           UIImageRenderingModeAlwaysTemplate]];
  [button addTarget:self.delegate
                action:@selector(saveCardBadgeButtonTapped:)
      forControlEvents:UIControlEventTouchUpInside];
  button.accessibilityIdentifier = kBadgeButtonSaveCardAccessibilityIdentifier;
  button.accessibilityLabel =
      l10n_util::GetNSString(IDS_IOS_AUTOFILL_SAVE_CARD_BADGE_HINT);
  return button;
}

- (BadgeButton*)translateBadgeButton {
  UIImage* image;
  image = UseSymbols() ? CustomSymbolWithPointSize(kTranslateSymbol,
                                                   kSymbolImagePointSize)
                       : [UIImage imageNamed:@"infobar_translate_icon"];
  BadgeButton* button =
      [self createButtonForType:kBadgeTypeTranslate
                          image:[image imageWithRenderingMode:
                                           UIImageRenderingModeAlwaysTemplate]];
  [button addTarget:self.delegate
                action:@selector(translateBadgeButtonTapped:)
      forControlEvents:UIControlEventTouchUpInside];
  button.accessibilityIdentifier = kBadgeButtonTranslateAccessibilityIdentifier;
  button.accessibilityLabel =
      l10n_util::GetNSString(IDS_IOS_INFOBAR_BADGES_TRANSLATE_HINT);
  return button;
}

- (BadgeButton*)incognitoBadgeButton {
  BadgeButton* button;
  if (UseSymbols()) {
    UIImage* image;
    if (@available(iOS 15, *)) {
      image = CustomPaletteSymbol(
          kIncognitoCircleFillSymbol, kSymbolIncognitoPointSize,
          UIImageSymbolWeightMedium, UIImageSymbolScaleMedium, @[
            [UIColor colorNamed:kGrey400Color],
            [UIColor colorNamed:kGrey100Color]
          ]);
    } else {
      image = [[UIImage imageNamed:@"incognito_badge_ios14"]
          imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
    }
    button = [self createButtonForType:kBadgeTypeIncognito image:image];
    button.fullScreenImage = CustomSymbolTemplateWithPointSize(
        kIncognitoSymbol, kSymbolIncognitoFullScreenPointSize);
  } else {
    button =
        [self createButtonForType:kBadgeTypeIncognito
                            image:[[UIImage imageNamed:@"incognito_badge"]
                                      imageWithRenderingMode:
                                          UIImageRenderingModeAlwaysOriginal]];
    button.fullScreenImage = [[UIImage imageNamed:@"incognito_small_badge"]
        imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
  }

  button.tintColor = [UIColor colorNamed:kTextPrimaryColor];
  button.accessibilityTraits &= ~UIAccessibilityTraitButton;
  button.userInteractionEnabled = NO;
  button.accessibilityIdentifier = kBadgeButtonIncognitoAccessibilityIdentifier;
  button.accessibilityLabel =
      l10n_util::GetNSString(IDS_IOS_BADGE_INCOGNITO_HINT);
  return button;
}

- (BadgeButton*)overflowBadgeButton {
  UIImage* image = UseSymbols()
                       ? DefaultSymbolWithPointSize(kEllipsisCircleFillSymbol,
                                                    kSymbolImagePointSize)
                       : [UIImage imageNamed:@"wrench_badge"];
  BadgeButton* button =
      [self createButtonForType:kBadgeTypeOverflow
                          image:[image imageWithRenderingMode:
                                           UIImageRenderingModeAlwaysTemplate]];
  button.accessibilityIdentifier = kBadgeButtonOverflowAccessibilityIdentifier;
  button.accessibilityLabel =
      l10n_util::GetNSString(IDS_IOS_OVERFLOW_BADGE_HINT);

  // Configure new overflow popup menu if enabled.
  if (UseSymbols()) {
    button.showsMenuAsPrimaryAction = YES;

    // Adds an empty menu so the event triggers the first time.
    button.menu = [UIMenu menuWithChildren:@[]];
    [button removeActionForIdentifier:kOverflowPopupMenuActionIdentifier
                     forControlEvents:UIControlEventMenuActionTriggered];

    // Configure actions that should be executed on each tap of the overflow
    // badge button to make sure the right overflow menu items are showing up.
    __weak UIButton* weakButton = button;
    __weak BadgeButtonFactory* weakSelf = self;
    void (^showModalFunction)(BadgeType) = ^(BadgeType badgeType) {
      [weakSelf.delegate showModalForBadgeType:badgeType];
    };
    void (^buttonTapHandler)(UIAction*) = ^(UIAction* action) {
      [weakSelf.delegate overflowBadgeButtonTapped:weakButton];
      weakButton.menu = GetOverflowMenuFromBadgeTypes(
          weakSelf.delegate.badgeTypesForOverflowMenu, showModalFunction);
    };
    UIAction* action =
        [UIAction actionWithTitle:@""
                            image:nil
                       identifier:kOverflowPopupMenuActionIdentifier
                          handler:buttonTapHandler];

    // Attach the action to the button.
    [button addAction:action
        forControlEvents:UIControlEventMenuActionTriggered];
  } else {
    [button addTarget:self.delegate
                  action:@selector(overflowBadgeButtonTapped:)
        forControlEvents:UIControlEventTouchUpInside];
  }
  return button;
}

- (BadgeButton*)saveAddressProfileBadgeButton {
  UIImage* image;
  image = UseSymbols() ? DefaultSymbolWithPointSize(kPinFillSymbol,
                                                    kSymbolImagePointSize)
                       : [UIImage imageNamed:@"ic_place"];

  BadgeButton* button =
      [self createButtonForType:kBadgeTypeSaveAddressProfile
                          image:[image imageWithRenderingMode:
                                           UIImageRenderingModeAlwaysTemplate]];
  [button addTarget:self.delegate
                action:@selector(saveAddressProfileBadgeButtonTapped:)
      forControlEvents:UIControlEventTouchUpInside];
  button.accessibilityIdentifier =
      kBadgeButtonSaveAddressProfileAccessibilityIdentifier;
  // TODO(crbug.com/1014652): Create a11y label hint.
  return button;
}

- (BadgeButton*)readingListBadgeButton {
  BadgeButton* button =
      [self createButtonForType:kBadgeTypeAddToReadingList
                          image:[[UIImage imageNamed:@"infobar_reading_list"]
                                    imageWithRenderingMode:
                                        UIImageRenderingModeAlwaysTemplate]];
  [button addTarget:self.delegate
                action:@selector(addToReadingListBadgeButtonTapped:)
      forControlEvents:UIControlEventTouchUpInside];
  button.accessibilityIdentifier =
      kBadgeButtonReadingListAccessibilityIdentifier;
  // TODO(crbug.com/1014652): Create a11y label hint.
  return button;
}

- (BadgeButton*)permissionsCameraBadgeButton {
  BadgeButton* button =
      [self createButtonForType:kBadgeTypePermissionsCamera
                          image:CustomSymbolTemplateWithPointSize(
                                    kCameraFillSymbol, kSymbolImagePointSize)];
  [button addTarget:self.delegate
                action:@selector(permissionsBadgeButtonTapped:)
      forControlEvents:UIControlEventTouchUpInside];
  button.accessibilityIdentifier =
      kBadgeButtonPermissionsCameraAccessibilityIdentifier;
  button.accessibilityLabel =
      l10n_util::GetNSString(IDS_IOS_INFOBAR_BADGES_PERMISSIONS_HINT);
  return button;
}

- (BadgeButton*)permissionsMicrophoneBadgeButton {
  BadgeButton* button = [self
      createButtonForType:kBadgeTypePermissionsMicrophone
                    image:DefaultSymbolTemplateWithPointSize(
                              kMicrophoneFillSymbol, kSymbolImagePointSize)];
  [button addTarget:self.delegate
                action:@selector(permissionsBadgeButtonTapped:)
      forControlEvents:UIControlEventTouchUpInside];
  button.accessibilityIdentifier =
      kBadgeButtonPermissionsMicrophoneAccessibilityIdentifier;
  button.accessibilityLabel =
      l10n_util::GetNSString(IDS_IOS_INFOBAR_BADGES_PERMISSIONS_HINT);
  return button;
}

- (BadgeButton*)createButtonForType:(BadgeType)badgeType image:(UIImage*)image {
  BadgeButton* button = [BadgeButton badgeButtonWithType:badgeType];
  UIImageSymbolConfiguration* symbolConfig = [UIImageSymbolConfiguration
      configurationWithPointSize:kSymbolImagePointSize
                          weight:UIImageSymbolWeightRegular
                           scale:UIImageSymbolScaleMedium];
  [button setPreferredSymbolConfiguration:symbolConfig
                          forImageInState:UIControlStateNormal];
  button.image = image;
  button.fullScreenOn = NO;
  button.imageView.contentMode = UIViewContentModeScaleAspectFit;
  [NSLayoutConstraint
      activateConstraints:@[ [button.widthAnchor
                              constraintEqualToAnchor:button.heightAnchor] ]];
  return button;
}

@end
