// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import <XCTest/XCTest.h>

#import "base/ios/ios_util.h"
#import "base/mac/foundation_util.h"
#import "components/signin/public/base/signin_switches.h"
#import "ios/chrome/browser/ui/authentication/signin_earl_grey.h"
#import "ios/chrome/browser/ui/authentication/signin_earl_grey_ui_test_util.h"
#import "ios/chrome/browser/ui/elements/activity_overlay_egtest_util.h"
#import "ios/chrome/browser/ui/elements/elements_constants.h"
#import "ios/chrome/browser/ui/popup_menu/popup_menu_constants.h"
#import "ios/chrome/browser/ui/settings/cells/clear_browsing_data_constants.h"
#import "ios/chrome/grit/ios_strings.h"
#import "ios/chrome/test/earl_grey/chrome_actions.h"
#import "ios/chrome/test/earl_grey/chrome_earl_grey.h"
#import "ios/chrome/test/earl_grey/chrome_earl_grey_ui.h"
#import "ios/chrome/test/earl_grey/chrome_matchers.h"
#import "ios/chrome/test/earl_grey/chrome_test_case.h"
#import "ios/public/provider/chrome/browser/signin/fake_chrome_identity.h"
#import "ios/testing/earl_grey/earl_grey_test.h"
#import "ui/base/l10n/l10n_util.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {

// Identifier used to find the 'Learn more' link.
NSString* const kLearnMoreIdentifier = @"Learn more";

// URL of the help center page.
char kHelpCenterURL[] = "support.google.com";

// Matcher for an element with or without the
// UIAccessibilityTraitSelected accessibility trait depending on `selected`.
id<GREYMatcher> ElementIsSelected(BOOL selected) {
  return selected
             ? grey_accessibilityTrait(UIAccessibilityTraitSelected)
             : grey_not(grey_accessibilityTrait(UIAccessibilityTraitSelected));
}

// Returns a matcher (which always matches) that records the selection
// state of matched element in `selected` parameter.
id<GREYMatcher> RecordElementSelectionState(BOOL& selected) {
  GREYMatchesBlock matches = ^BOOL(UIView* view) {
    selected = ([view accessibilityTraits] & UIAccessibilityTraitSelected) != 0;
    return YES;
  };
  GREYDescribeToBlock describe = ^void(id<GREYDescription> description) {
    [description appendText:@"Selected Check"];
  };

  return [[GREYElementMatcherBlock alloc] initWithMatchesBlock:matches
                                              descriptionBlock:describe];
}

}  // namespace

using chrome_test_util::ButtonWithAccessibilityLabel;
using chrome_test_util::ClearAutofillButton;
using chrome_test_util::ClearBrowsingHistoryButton;
using chrome_test_util::ClearCookiesButton;
using chrome_test_util::ClearCacheButton;
using chrome_test_util::ClearSavedPasswordsButton;
using chrome_test_util::SettingsDoneButton;
using chrome_test_util::SettingsMenuPrivacyButton;
using chrome_test_util::WindowWithNumber;

@interface ClearBrowsingDataSettingsTestCase : ChromeTestCase
@end

@implementation ClearBrowsingDataSettingsTestCase

- (AppLaunchConfiguration)appConfigurationForTestCase {
  AppLaunchConfiguration config = [super appConfigurationForTestCase];
  if ([self isRunningTest:@selector(testTapLearnMore)] ||
      [self isRunningTest:@selector(testTapLearnMoreFromHistory)] ||
      [self
          isRunningTest:@selector(testUserSignedOutWhenClearingBrowsingData)]) {
    config.features_disabled.push_back(switches::kEnableCbdSignOut);
  } else if ([self isRunningTest:@selector
                   (testUserSignedInWhenClearingBrowsingData)]) {
    config.features_enabled.push_back(switches::kEnableCbdSignOut);
  }
  return config;
}

- (void)openClearBrowsingDataDialog {
  [ChromeEarlGreyUI openSettingsMenu];
  [ChromeEarlGreyUI tapSettingsMenuButton:SettingsMenuPrivacyButton()];

  NSString* clearBrowsingDataDialogLabel =
      l10n_util::GetNSString(IDS_IOS_CLEAR_BROWSING_DATA_TITLE);
  [ChromeEarlGreyUI tapPrivacyMenuButton:ButtonWithAccessibilityLabel(
                                             clearBrowsingDataDialogLabel)];
}

- (void)openClearBrowsingDataDialogInWindowWithNumber:(int)windowNumber {
  [ChromeEarlGreyUI openSettingsMenuInWindowWithNumber:windowNumber];
  [ChromeEarlGreyUI tapSettingsMenuButton:SettingsMenuPrivacyButton()];

  NSString* clearBrowsingDataDialogLabel =
      l10n_util::GetNSString(IDS_IOS_CLEAR_BROWSING_DATA_TITLE);
  [ChromeEarlGreyUI tapPrivacyMenuButton:ButtonWithAccessibilityLabel(
                                             clearBrowsingDataDialogLabel)];
}

// Test that opening the clear browsing data dialog does not crash.
- (void)testOpenClearBrowsingDataDialogUI {
  [self openClearBrowsingDataDialog];
  [[EarlGrey selectElementWithMatcher:SettingsDoneButton()]
      performAction:grey_tap()];
}

// Verifies that the CBD screen can be swiped down to dismiss.
- (void)testClearBrowsingDataSwipeDown {
  [self openClearBrowsingDataDialog];

  // Check that CBD is presented.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kClearBrowsingDataViewAccessibilityIdentifier)]
      assertWithMatcher:grey_notNil()];

  // Swipe TableView down.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kClearBrowsingDataViewAccessibilityIdentifier)]
      performAction:grey_swipeFastInDirection(kGREYDirectionDown)];

  // Check that Settings has been dismissed.
  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kClearBrowsingDataViewAccessibilityIdentifier)]
      assertWithMatcher:grey_nil()];
}

// Tests that opening the clear browsing data dialog in two windows does not
// crash.
- (void)testClearBrowsingDataDialogInMultiWindow {
  if (![ChromeEarlGrey areMultipleWindowsSupported])
    EARL_GREY_TEST_DISABLED(@"Multiple windows can't be opened.");

  // TODO(crbug.com/1285974).
  if ([ChromeEarlGrey isNewOverflowMenuEnabled]) {
    EARL_GREY_TEST_DISABLED(
        @"Earl Grey doesn't work properly with SwiftUI and multiwindow");
  }

  [ChromeEarlGrey openNewWindow];
  [ChromeEarlGrey waitUntilReadyWindowWithNumber:1];
  [ChromeEarlGrey waitForForegroundWindowCount:2];

  [self openClearBrowsingDataDialogInWindowWithNumber:0];
  [self openClearBrowsingDataDialogInWindowWithNumber:1];

  // Grab start states.
  [EarlGrey setRootMatcherForSubsequentInteractions:WindowWithNumber(0)];
  BOOL isClearBrowsingHistoryButtonSelected = NO;
  BOOL isClearCookiesButtonSelected = NO;
  BOOL isClearCacheButtonSelected = NO;
  BOOL isClearSavedPasswordsButtonSelected = NO;
  BOOL isClearAutofillButtonSelected = NO;
  [[EarlGrey selectElementWithMatcher:ClearBrowsingHistoryButton()]
      assertWithMatcher:RecordElementSelectionState(
                            isClearBrowsingHistoryButtonSelected)];
  [[EarlGrey selectElementWithMatcher:ClearCookiesButton()]
      assertWithMatcher:RecordElementSelectionState(
                            isClearCookiesButtonSelected)];
  [[EarlGrey selectElementWithMatcher:ClearCacheButton()]
      assertWithMatcher:RecordElementSelectionState(
                            isClearCacheButtonSelected)];
  [[EarlGrey selectElementWithMatcher:ClearSavedPasswordsButton()]
      assertWithMatcher:RecordElementSelectionState(
                            isClearSavedPasswordsButtonSelected)];
  [[EarlGrey selectElementWithMatcher:ClearAutofillButton()]
      assertWithMatcher:RecordElementSelectionState(
                            isClearAutofillButtonSelected)];

  // Verify it matches second window.
  [EarlGrey setRootMatcherForSubsequentInteractions:WindowWithNumber(1)];
  [[EarlGrey selectElementWithMatcher:ClearBrowsingHistoryButton()]
      assertWithMatcher:ElementIsSelected(
                            isClearBrowsingHistoryButtonSelected)];
  [[EarlGrey selectElementWithMatcher:ClearCookiesButton()]
      assertWithMatcher:ElementIsSelected(isClearCookiesButtonSelected)];
  [[EarlGrey selectElementWithMatcher:ClearCacheButton()]
      assertWithMatcher:ElementIsSelected(isClearCacheButtonSelected)];
  [[EarlGrey selectElementWithMatcher:ClearSavedPasswordsButton()]
      assertWithMatcher:ElementIsSelected(isClearSavedPasswordsButtonSelected)];
  [[EarlGrey selectElementWithMatcher:ClearAutofillButton()]
      assertWithMatcher:ElementIsSelected(isClearAutofillButtonSelected)];

  // Switch Clear Browsing History Button in window 0 and make sure it is
  // deselected in both.
  [EarlGrey setRootMatcherForSubsequentInteractions:WindowWithNumber(0)];
  [[EarlGrey selectElementWithMatcher:ClearBrowsingHistoryButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:ClearBrowsingHistoryButton()]
      assertWithMatcher:ElementIsSelected(
                            !isClearBrowsingHistoryButtonSelected)];
  [EarlGrey setRootMatcherForSubsequentInteractions:WindowWithNumber(1)];
  [[EarlGrey selectElementWithMatcher:ClearBrowsingHistoryButton()]
      assertWithMatcher:ElementIsSelected(
                            !isClearBrowsingHistoryButtonSelected)];

  // Switch Clear Browsing History Button in window 1 and make sure it is
  // deselected in both.
  [[EarlGrey selectElementWithMatcher:ClearCookiesButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:ClearCookiesButton()]
      assertWithMatcher:ElementIsSelected(!isClearCookiesButtonSelected)];
  [EarlGrey setRootMatcherForSubsequentInteractions:WindowWithNumber(0)];
  [[EarlGrey selectElementWithMatcher:ClearCookiesButton()]
      assertWithMatcher:ElementIsSelected(!isClearCookiesButtonSelected)];

  // Switch Clear Cache Button in window 0 and make sure it is
  // deselected in both.
  [[EarlGrey selectElementWithMatcher:ClearCacheButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:ClearCacheButton()]
      assertWithMatcher:ElementIsSelected(!isClearCacheButtonSelected)];
  [EarlGrey setRootMatcherForSubsequentInteractions:WindowWithNumber(1)];
  [[EarlGrey selectElementWithMatcher:ClearCacheButton()]
      assertWithMatcher:ElementIsSelected(!isClearCacheButtonSelected)];

  // Switch Clear Saved Passwords Button in window 1 and make sure it is
  // deselected in both.
  [[EarlGrey selectElementWithMatcher:ClearSavedPasswordsButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:ClearSavedPasswordsButton()]
      assertWithMatcher:ElementIsSelected(
                            !isClearSavedPasswordsButtonSelected)];
  [EarlGrey setRootMatcherForSubsequentInteractions:WindowWithNumber(0)];
  [[EarlGrey selectElementWithMatcher:ClearSavedPasswordsButton()]
      assertWithMatcher:ElementIsSelected(
                            !isClearSavedPasswordsButtonSelected)];

  // Switch Clear Autofill Button in window 0 and make sure it is
  // deselected in both.
  [[EarlGrey selectElementWithMatcher:ClearAutofillButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:ClearAutofillButton()]
      assertWithMatcher:ElementIsSelected(!isClearAutofillButtonSelected)];
  [EarlGrey setRootMatcherForSubsequentInteractions:WindowWithNumber(1)];
  [[EarlGrey selectElementWithMatcher:ClearAutofillButton()]
      assertWithMatcher:ElementIsSelected(!isClearAutofillButtonSelected)];

  // Restore to intial state.
  [EarlGrey setRootMatcherForSubsequentInteractions:WindowWithNumber(0)];
  [[EarlGrey selectElementWithMatcher:ClearBrowsingHistoryButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:ClearCookiesButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:ClearCacheButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:ClearSavedPasswordsButton()]
      performAction:grey_tap()];
  [[EarlGrey selectElementWithMatcher:ClearAutofillButton()]
      performAction:grey_tap()];

  // Cleanup.
  [[EarlGrey selectElementWithMatcher:SettingsDoneButton()]
      performAction:grey_tap()];
  [EarlGrey setRootMatcherForSubsequentInteractions:WindowWithNumber(1)];
  [[EarlGrey selectElementWithMatcher:SettingsDoneButton()]
      performAction:grey_tap()];
}

// Tests that tapping the "Learn more" link opens the help center.
- (void)testTapLearnMore {
  [self openClearBrowsingDataDialog];

  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kClearBrowsingDataViewAccessibilityIdentifier)]
      performAction:grey_swipeFastInDirection(kGREYDirectionUp)];
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(grey_accessibilityLabel(
                                              kLearnMoreIdentifier),
                                          grey_kindOfClassName(
                                              @"UIAccessibilityLinkSubelement"),
                                          nil)]
      performAction:chrome_test_util::TapAtPointPercentage(0.95, 0.05)];

  // Check that the URL of the help center was opened.
  GREYAssertEqual(kHelpCenterURL, [ChromeEarlGrey webStateVisibleURL].host(),
                  @"Did not navigate to the help center url.");
}

// Tests that opening the Clear Browsing interface from the History and tapping
// the "Learn more" link opens the help center.
- (void)testTapLearnMoreFromHistory {
  [ChromeEarlGreyUI openToolsMenu];
  [ChromeEarlGreyUI
      tapToolsMenuButton:chrome_test_util::HistoryDestinationButton()];
  [[EarlGrey selectElementWithMatcher:chrome_test_util::
                                          HistoryClearBrowsingDataButton()]
      performAction:grey_tap()];

  [[EarlGrey
      selectElementWithMatcher:
          grey_accessibilityID(kClearBrowsingDataViewAccessibilityIdentifier)]
      performAction:grey_swipeFastInDirection(kGREYDirectionUp)];
  [[EarlGrey
      selectElementWithMatcher:grey_allOf(grey_accessibilityLabel(
                                              kLearnMoreIdentifier),
                                          grey_kindOfClassName(
                                              @"UIAccessibilityLinkSubelement"),
                                          nil)]
      performAction:chrome_test_util::TapAtPointPercentage(0.95, 0.05)];

  // Check that the URL of the help center was opened.
  GREYAssertEqual(kHelpCenterURL, [ChromeEarlGrey webStateVisibleURL].host(),
                  @"Did not navigate to the help center url.");
}

// Sign-in without sync. Clear browsing data.
- (void)signInOpenCBDAndClearDataWithFakeIdentity:
    (FakeChromeIdentity*)fakeIdentity {
  [SigninEarlGrey addFakeIdentity:fakeIdentity];
  [SigninEarlGreyUI signinWithFakeIdentity:fakeIdentity enableSync:NO];

  [ChromeEarlGreyUI openSettingsMenu];
  [ChromeEarlGreyUI tapSettingsMenuButton:SettingsMenuPrivacyButton()];
  [ChromeEarlGreyUI
      tapPrivacyMenuButton:chrome_test_util::ButtonWithAccessibilityLabelId(
                               IDS_IOS_CLEAR_BROWSING_DATA_TITLE)];
  [ChromeEarlGreyUI tapClearBrowsingDataMenuButton:
                        chrome_test_util::ClearBrowsingDataButton()];
  [[EarlGrey selectElementWithMatcher:chrome_test_util::
                                          ConfirmClearBrowsingDataButton()]
      performAction:grey_tap()];
  WaitForActivityOverlayToDisappear();
}

// Tests that a user in the `ConsentLevel::kSignin` state will be signed out
// after clearing their browsing history if `kEnableCbdSignOut` feature is
// enabled.
- (void)testUserSignedInWhenClearingBrowsingData {
  FakeChromeIdentity* fakeIdentity = [FakeChromeIdentity fakeIdentity1];
  [self signInOpenCBDAndClearDataWithFakeIdentity:fakeIdentity];
  [SigninEarlGrey verifySignedInWithFakeIdentity:fakeIdentity];
}

// Tests that a user in the `ConsentLevel::kSignin` state will be signed out
// after clearing their browsing history if `kEnableCbdSignOut` feature is
// disabled.
- (void)testUserSignedOutWhenClearingBrowsingData {
  FakeChromeIdentity* fakeIdentity = [FakeChromeIdentity fakeIdentity1];
  [self signInOpenCBDAndClearDataWithFakeIdentity:fakeIdentity];
  [SigninEarlGrey verifySignedOut];
}

@end
