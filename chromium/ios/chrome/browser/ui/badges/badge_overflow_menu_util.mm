// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/badges/badge_overflow_menu_util.h"

#import "base/metrics/histogram_functions.h"
#import "base/notreached.h"
#import "components/password_manager/core/common/password_manager_features.h"
#import "components/strings/grit/components_strings.h"
#import "ios/chrome/browser/ui/badges/badge_constants.h"
#import "ios/chrome/browser/ui/badges/badges_histograms.h"
#import "ios/chrome/browser/ui/icons/action_icon.h"
#import "ios/chrome/browser/ui/icons/chrome_symbol.h"
#import "ios/chrome/browser/ui/icons/infobar_icon.h"
#import "ios/chrome/grit/ios_strings.h"
#import "ui/base/l10n/l10n_util.h"

#import <UIKit/UIKit.h>

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {

// The image used for password related badges.
UIImage* GetPasswordImage() {
  if (UseSymbols()) {
    return CustomSymbolTemplateWithPointSize(kPasswordSymbol,
                                             kSymbolImagePointSize);
  } else {
    NSString* passwordImageName =
        base::FeatureList::IsEnabled(
            password_manager::features::kIOSEnablePasswordManagerBrandingUpdate)
            ? @"password_key"
            : @"legacy_password_key";
    return [[UIImage imageNamed:passwordImageName]
        imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
  }
}

// The menu element for `badgeType` shown in the overflow menu when the overflow
// badge is tapped.
UIAction* GetOverflowMenuElementForBadgeType(
    BadgeType badge_type,
    ShowModalFunction show_modal_function) {
  NSString* title;
  UIActionIdentifier action_identifier;
  UIImage* image;
  MobileMessagesInfobarType histogram_type = MobileMessagesInfobarType::Confirm;

  switch (badge_type) {
    case kBadgeTypePasswordSave:
      action_identifier = kBadgeButtonSavePasswordActionIdentifier;
      title =
          l10n_util::GetNSString(IDS_IOS_PASSWORD_MANAGER_SAVE_PASSWORD_TITLE);
      image = GetPasswordImage();
      histogram_type = MobileMessagesInfobarType::SavePassword;
      break;
    case kBadgeTypePasswordUpdate:
      action_identifier = kBadgeButtonUpdatePasswordActionIdentifier;
      title = l10n_util::GetNSString(
          IDS_IOS_PASSWORD_MANAGER_UPDATE_PASSWORD_TITLE);
      image = GetPasswordImage();
      histogram_type = MobileMessagesInfobarType::UpdatePassword;
      break;
    case kBadgeTypeSaveAddressProfile:
      action_identifier = kBadgeButtonSaveAddressProfileActionIdentifier;
      title =
          l10n_util::GetNSString(IDS_IOS_AUTOFILL_SAVE_ADDRESS_PROMPT_TITLE);

      image = UseSymbols() ? DefaultSymbolWithPointSize(kPinSymbol,
                                                        kSymbolImagePointSize)
                           : [UIImage imageNamed:@"ic_place"];
      image = [image imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
      histogram_type = MobileMessagesInfobarType::AutofillSaveAddressProfile;
      break;
    case kBadgeTypeSaveCard:
      action_identifier = kBadgeButtonSaveCardActionIdentifier;
      title = l10n_util::GetNSString(IDS_IOS_AUTOFILL_SAVE_CARD);
      image = UseSymbols() ? DefaultSymbolWithPointSize(kCreditCardSymbol,
                                                        kSymbolImagePointSize)
                           : [UIImage imageNamed:@"infobar_save_card_icon"];
      image = [image imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
      histogram_type = MobileMessagesInfobarType::SaveCard;
      break;
    case kBadgeTypeTranslate:
      action_identifier = kBadgeButtonTranslateActionIdentifier;
      title = l10n_util::GetNSString(IDS_IOS_TRANSLATE_INFOBAR_MODAL_TITLE);
      image = UseSymbols() ? CustomSymbolWithPointSize(kTranslateSymbol,
                                                       kSymbolImagePointSize)
                           : [UIImage imageNamed:@"infobar_translate_icon"];
      image = [image imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
      break;
    case kBadgeTypeAddToReadingList:
      action_identifier = kBadgeButtonReadingListActionIdentifier;
      title = l10n_util::GetNSString(IDS_IOS_READING_LIST_MESSAGES_MODAL_TITLE);
      image = UseSymbols() ? DefaultSymbolWithPointSize(kReadLaterActionSymbol,
                                                        kSymbolImagePointSize)
                           : [UIImage imageNamed:@"infobar_reading_list"];
      histogram_type = MobileMessagesInfobarType::Translate;
      break;
    case kBadgeTypePermissionsCamera:
      action_identifier = kBadgeButtonPermissionsActionIdentifier;
      title = l10n_util::GetNSString(
          IDS_IOS_PERMISSIONS_INFOBAR_OVERFLOW_POPUP_TITLE);
      image = [CustomSymbolWithPointSize(kCameraSymbol, kSymbolImagePointSize)
          imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
      histogram_type = MobileMessagesInfobarType::Permissions;
      break;
    case kBadgeTypePermissionsMicrophone:
      action_identifier = kBadgeButtonPermissionsActionIdentifier;
      title = l10n_util::GetNSString(
          IDS_IOS_PERMISSIONS_INFOBAR_OVERFLOW_POPUP_TITLE);
      image = DefaultSymbolTemplateWithPointSize(kMicrophoneSymbol,
                                                 kSymbolImagePointSize);
      histogram_type = MobileMessagesInfobarType::Permissions;
      break;
    case kBadgeTypeIncognito:
      NOTREACHED() << "An overflow menu badge should not be an Incognito badge";
      break;
    case kBadgeTypeOverflow:
      NOTREACHED() << "A overflow menu badge should not be an overflow badge";
      break;
    case kBadgeTypeNone:
      NOTREACHED() << "A badge should not have kBadgeTypeNone";
      break;
  }

  UIActionHandler handler = ^(UIAction* action) {
    base::UmaHistogramEnumeration(kInfobarOverflowMenuTappedHistogram,
                                  histogram_type);
    show_modal_function(badge_type);
  };

  return [UIAction actionWithTitle:title
                             image:image
                        identifier:action_identifier
                           handler:handler];
}

}  // namespace

UIMenu* GetOverflowMenuFromBadgeTypes(NSArray<NSNumber*>* badge_types,
                                      ShowModalFunction show_modal_function) {
  NSMutableArray<UIMenuElement*>* menu_elements = [NSMutableArray array];
  for (NSNumber* badge_type_wrapped in badge_types) {
    BadgeType badgeType = BadgeType(badge_type_wrapped.unsignedIntegerValue);
    [menu_elements addObject:GetOverflowMenuElementForBadgeType(
                                 badgeType, show_modal_function)];
  }
  return [UIMenu menuWithTitle:@"" children:menu_elements];
}
