// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ios/chrome/browser/ui/ui_feature_flags.h"

BASE_FEATURE(kExpandedTabStrip,
             "ExpandedTabStrip",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kTestFeature, "TestFeature", base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kSharedHighlightingIOS,
             "SharedHighlightingIOS",
             base::FEATURE_ENABLED_BY_DEFAULT);

BASE_FEATURE(kEnableFREDefaultBrowserPromoScreen,
             "EnableFREDefaultBrowserPromoScreen",
             base::FEATURE_ENABLED_BY_DEFAULT);

BASE_FEATURE(kEnableFREUIModuleIOS,
             "EnableFREUIModuleIOSV3",
             base::FEATURE_ENABLED_BY_DEFAULT);

// TODO(crbug.com/1128242): Remove this flag after the refactoring work is
// finished.
BASE_FEATURE(kModernTabStrip,
             "ModernTabStrip",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kIncognitoNtpRevamp,
             "IncognitoNtpRevamp",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kIOS3PIntentsInIncognito,
             "IOS3pIntentsInIncognito",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kDefaultBrowserFullscreenPromoExperiment,
             "DefaultBrowserFullscreenPromoExperiment",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kDefaultBrowserIntentsShowSettings,
             "DefaultBrowserIntentsShowSettings",
             base::FEATURE_ENABLED_BY_DEFAULT);

BASE_FEATURE(kIOSNewOmniboxImplementation,
             "kIOSNewOmniboxImplementation",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kIOSOmniboxUpdatedPopupUI,
             "IOSOmniboxUpdatedPopupUI",
             base::FEATURE_DISABLED_BY_DEFAULT);

const char kIOSOmniboxUpdatedPopupUIVariationName[] = "PopupUIVariant";

extern const char kIOSOmniboxUpdatedPopupUIVariation1[] = "variant-one";
extern const char kIOSOmniboxUpdatedPopupUIVariation2[] = "variant-two";
extern const char kIOSOmniboxUpdatedPopupUIVariation1UIKit[] =
    "variant-one-UIKit";
extern const char kIOSOmniboxUpdatedPopupUIVariation2UIKit[] =
    "variant-two-UIKit";

BASE_FEATURE(kIOSLocationBarUseNativeContextMenu,
             "IOSLocationBarUseNativeContextMenu",
             base::FEATURE_ENABLED_BY_DEFAULT);

BASE_FEATURE(kUpdateHistoryEntryPointsInIncognito,
             "UpdateHistoryEntryPointsInIncognito",
             base::FEATURE_ENABLED_BY_DEFAULT);

BASE_FEATURE(kUseLensToSearchForImage,
             "UseLensToSearchForImage",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kEnableLensInHomeScreenWidget,
             "EnableLensInHomeScreenWidget",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kEnableLensInKeyboard,
             "EnableLensInKeyboard",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kEnableLensInNTP,
             "EnableLensInNTP",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kRemoveExcessNTPs,
             "RemoveExcessNTPs",
             base::FEATURE_ENABLED_BY_DEFAULT);

BASE_FEATURE(kEnableShortenedPasswordAutoFillInstruction,
             "EnableShortenedPasswordAutoFillInstruction",
             base::FEATURE_ENABLED_BY_DEFAULT);

BASE_FEATURE(kUseSFSymbols, "UseSFSymbols", base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kCalendarExperienceKit,
             "CalendarExperienceKit",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kEnableExpKitAppleCalendar,
             "EnableExpKitAppleCalendar",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kEnablePhoneNumbers,
             "EnablePhoneNumbers",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kMapsExperienceKit,
             "MapsExperienceKit",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kEnableMiniMap,
             "EnableMiniMap",
             base::FEATURE_DISABLED_BY_DEFAULT);
