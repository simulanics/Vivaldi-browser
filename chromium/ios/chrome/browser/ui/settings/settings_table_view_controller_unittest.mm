// Copyright 2020 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/settings/settings_table_view_controller.h"

#import "base/strings/sys_string_conversions.h"
#import "base/test/task_environment.h"
#import "components/password_manager/core/browser/password_manager_test_utils.h"
#import "components/password_manager/core/browser/test_password_store.h"
#import "components/policy/core/common/policy_loader_ios_constants.h"
#import "components/policy/policy_constants.h"
#import "components/signin/public/base/signin_pref_names.h"
#import "components/sync/test/mock_sync_service.h"
#import "ios/chrome/browser/browser_state/test_chrome_browser_state.h"
#import "ios/chrome/browser/main/test_browser.h"
#import "ios/chrome/browser/passwords/ios_chrome_password_store_factory.h"
#import "ios/chrome/browser/policy/policy_util.h"
#import "ios/chrome/browser/prefs/pref_names.h"
#import "ios/chrome/browser/search_engines/template_url_service_factory.h"
#import "ios/chrome/browser/signin/authentication_service_factory.h"
#import "ios/chrome/browser/signin/authentication_service_fake.h"
#import "ios/chrome/browser/sync/mock_sync_service_utils.h"
#import "ios/chrome/browser/sync/sync_service_factory.h"
#import "ios/chrome/browser/sync/sync_setup_service.h"
#import "ios/chrome/browser/sync/sync_setup_service_factory.h"
#import "ios/chrome/browser/sync/sync_setup_service_mock.h"
#import "ios/chrome/browser/ui/commands/application_commands.h"
#import "ios/chrome/browser/ui/commands/browsing_data_commands.h"
#import "ios/chrome/browser/ui/commands/command_dispatcher.h"
#import "ios/chrome/browser/ui/commands/snackbar_commands.h"
#import "ios/chrome/browser/ui/settings/settings_table_view_controller_constants.h"
#import "ios/chrome/browser/ui/table_view/cells/table_view_detail_icon_item.h"
#import "ios/chrome/browser/ui/table_view/cells/table_view_image_item.h"
#import "ios/chrome/browser/ui/table_view/cells/table_view_info_button_item.h"
#import "ios/chrome/browser/ui/table_view/chrome_table_view_controller_test.h"
#import "ios/chrome/grit/ios_chromium_strings.h"
#import "ios/chrome/grit/ios_strings.h"
#import "ios/chrome/test/ios_chrome_scoped_testing_local_state.h"
#import "ios/public/provider/chrome/browser/signin/fake_chrome_identity.h"
#import "ios/web/public/test/web_task_environment.h"
#import "testing/gtest/include/gtest/gtest.h"
#import "testing/gtest_mac.h"
#import "third_party/ocmock/OCMock/OCMock.h"
#import "third_party/ocmock/gtest_support.h"
#import "ui/base/l10n/l10n_util_mac.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

using ::testing::NiceMock;
using ::testing::Return;
using web::WebTaskEnvironment;

class SettingsTableViewControllerTest : public ChromeTableViewControllerTest {
 public:
  void SetUp() override {
    ChromeTableViewControllerTest::SetUp();

    TestChromeBrowserState::Builder builder;
    builder.AddTestingFactory(SyncServiceFactory::GetInstance(),
                              base::BindRepeating(&CreateMockSyncService));
    builder.AddTestingFactory(
        SyncSetupServiceFactory::GetInstance(),
        base::BindRepeating(&SyncSetupServiceMock::CreateKeyedService));
    builder.AddTestingFactory(
        ios::TemplateURLServiceFactory::GetInstance(),
        ios::TemplateURLServiceFactory::GetDefaultFactory());
    builder.AddTestingFactory(
        AuthenticationServiceFactory::GetInstance(),
        base::BindRepeating(
            &AuthenticationServiceFake::CreateAuthenticationService));
    chrome_browser_state_ = builder.Build();

    browser_ = std::make_unique<TestBrowser>(chrome_browser_state_.get());

    sync_setup_service_mock_ = static_cast<SyncSetupServiceMock*>(
        SyncSetupServiceFactory::GetForBrowserState(
            chrome_browser_state_.get()));
    sync_service_mock_ = static_cast<syncer::MockSyncService*>(
        SyncServiceFactory::GetForBrowserState(chrome_browser_state_.get()));

    auth_service_ = static_cast<AuthenticationServiceFake*>(
        AuthenticationServiceFactory::GetInstance()->GetForBrowserState(
            chrome_browser_state_.get()));

    password_store_mock_ =
        base::WrapRefCounted(static_cast<password_manager::TestPasswordStore*>(
            IOSChromePasswordStoreFactory::GetInstance()
                ->SetTestingFactoryAndUse(
                    chrome_browser_state_.get(),
                    base::BindRepeating(&password_manager::BuildPasswordStore<
                                        web::BrowserState,
                                        password_manager::TestPasswordStore>))
                .get()));

    fake_identity_ = [FakeChromeIdentity identityWithEmail:@"foo1@gmail.com"
                                                    gaiaID:@"foo1ID"
                                                      name:@"Fake Foo 1"];

    // Make sure there is no pre-existing policy present.
    [[NSUserDefaults standardUserDefaults]
        removeObjectForKey:kPolicyLoaderIOSConfigurationKey];
  }

  void TearDown() override {
    // Cleanup any policies left from the test.
    [[NSUserDefaults standardUserDefaults]
        removeObjectForKey:kPolicyLoaderIOSConfigurationKey];

    [static_cast<SettingsTableViewController*>(controller())
        settingsWillBeDismissed];
    ChromeTableViewControllerTest::TearDown();
  }

  ChromeTableViewController* InstantiateController() override {
    id mockSnackbarCommandHandler =
        OCMProtocolMock(@protocol(SnackbarCommands));

    // Set up ApplicationCommands mock. Because ApplicationCommands conforms
    // to ApplicationSettingsCommands, that needs to be mocked and dispatched
    // as well.
    id mockApplicationCommandHandler =
        OCMProtocolMock(@protocol(ApplicationCommands));
    id mockApplicationSettingsCommandHandler =
        OCMProtocolMock(@protocol(ApplicationSettingsCommands));

    CommandDispatcher* dispatcher = browser_->GetCommandDispatcher();
    [dispatcher startDispatchingToTarget:mockSnackbarCommandHandler
                             forProtocol:@protocol(SnackbarCommands)];
    [dispatcher startDispatchingToTarget:mockApplicationCommandHandler
                             forProtocol:@protocol(ApplicationCommands)];
    [dispatcher
        startDispatchingToTarget:mockApplicationSettingsCommandHandler
                     forProtocol:@protocol(ApplicationSettingsCommands)];

    SettingsTableViewController* controller =
        [[SettingsTableViewController alloc]
            initWithBrowser:browser_.get()
                 dispatcher:static_cast<id<ApplicationCommands, BrowserCommands,
                                           BrowsingDataCommands>>(
                                browser_->GetCommandDispatcher())];
    controller.applicationCommandsHandler = HandlerForProtocol(
        browser_->GetCommandDispatcher(), ApplicationCommands);
    controller.snackbarCommandsHandler =
        HandlerForProtocol(browser_->GetCommandDispatcher(), SnackbarCommands);
    return controller;
  }

  void SetupSyncServiceEnabledExpectations() {
    ON_CALL(*sync_setup_service_mock_, CanSyncFeatureStart())
        .WillByDefault(Return(true));
    ON_CALL(*sync_setup_service_mock_, IsSyncingAllDataTypes())
        .WillByDefault(Return(true));
    ON_CALL(*sync_setup_service_mock_, IsInitialSetupOngoing())
        .WillByDefault(Return(true));
    ON_CALL(*sync_service_mock_, GetTransportState())
        .WillByDefault(Return(syncer::SyncService::TransportState::ACTIVE));
    ON_CALL(*sync_service_mock_->GetMockUserSettings(), IsFirstSetupComplete())
        .WillByDefault(Return(true));
    ON_CALL(*sync_service_mock_->GetMockUserSettings(), GetSelectedTypes())
        .WillByDefault(Return(syncer::UserSelectableTypeSet::All()));
    ON_CALL(*sync_service_mock_, HasSyncConsent()).WillByDefault(Return(true));
  }

  void AddSigninDisabledEnterprisePolicy() {
    NSDictionary* policy = @{
      base::SysUTF8ToNSString(policy::key::kBrowserSignin) : [NSNumber
          numberWithInt:static_cast<int>(BrowserSigninMode::kDisabled)]
    };

    [[NSUserDefaults standardUserDefaults]
        setObject:policy
           forKey:kPolicyLoaderIOSConfigurationKey];
  }

  PrefService* GetLocalState() { return scoped_testing_local_state_.Get(); }

 protected:
  // Needed for test browser state created by TestChromeBrowserState().
  web::WebTaskEnvironment task_environment_;
  IOSChromeScopedTestingLocalState scoped_testing_local_state_;

  FakeChromeIdentity* fake_identity_ = nullptr;
  AuthenticationServiceFake* auth_service_ = nullptr;
  syncer::MockSyncService* sync_service_mock_ = nullptr;
  SyncSetupServiceMock* sync_setup_service_mock_ = nullptr;
  scoped_refptr<password_manager::TestPasswordStore> password_store_mock_;

  std::unique_ptr<TestChromeBrowserState> chrome_browser_state_;
  std::unique_ptr<TestBrowser> browser_;

  SettingsTableViewController* controller_ = nullptr;
};

// Verifies that the Sync icon displays the on state when the user has turned
// on sync during sign-in.
TEST_F(SettingsTableViewControllerTest, SyncOn) {
  SetupSyncServiceEnabledExpectations();
  ON_CALL(*sync_setup_service_mock_, GetSyncServiceState())
      .WillByDefault(Return(SyncSetupService::kNoSyncServiceError));
  auth_service_->SignIn(fake_identity_);

  CreateController();
  CheckController();

  NSArray* account_items = [controller().tableViewModel
      itemsInSectionWithIdentifier:SettingsSectionIdentifier::
                                       SettingsSectionIdentifierAccount];
  ASSERT_EQ(3U, account_items.count);

  TableViewDetailIconItem* sync_item =
      static_cast<TableViewDetailIconItem*>(account_items[1]);
  ASSERT_NSEQ(sync_item.text,
              l10n_util::GetNSString(IDS_IOS_GOOGLE_SYNC_SETTINGS_TITLE));
  ASSERT_NSEQ(l10n_util::GetNSString(IDS_IOS_SETTING_ON), sync_item.detailText);
  ASSERT_EQ(UILayoutConstraintAxisHorizontal,
            sync_item.textLayoutConstraintAxis);
}

// Verifies that the Sync icon displays the sync password error when the user
// has turned on sync during sign-in, but not entered an existing encryption
// password.
TEST_F(SettingsTableViewControllerTest, SyncPasswordError) {
  SetupSyncServiceEnabledExpectations();
  // Set missing password error in Sync service.
  ON_CALL(*sync_setup_service_mock_, GetSyncServiceState())
      .WillByDefault(Return(SyncSetupService::kSyncServiceNeedsPassphrase));
  auth_service_->SignIn(fake_identity_);

  CreateController();
  CheckController();

  NSArray* account_items = [controller().tableViewModel
      itemsInSectionWithIdentifier:SettingsSectionIdentifier::
                                       SettingsSectionIdentifierAccount];
  ASSERT_EQ(3U, account_items.count);

  TableViewDetailIconItem* sync_item =
      static_cast<TableViewDetailIconItem*>(account_items[1]);
  ASSERT_NSEQ(sync_item.text,
              l10n_util::GetNSString(IDS_IOS_GOOGLE_SYNC_SETTINGS_TITLE));
  ASSERT_NSEQ(sync_item.detailText,
              l10n_util::GetNSString(IDS_IOS_SYNC_ENCRYPTION_DESCRIPTION));
  ASSERT_EQ(UILayoutConstraintAxisVertical, sync_item.textLayoutConstraintAxis);

  // Check that there is no sign-in promo when there is a sync error.
  ASSERT_FALSE([controller().tableViewModel
      hasSectionForSectionIdentifier:SettingsSectionIdentifier::
                                         SettingsSectionIdentifierSignIn]);
}

// Verifies that the Sync icon displays the off state when the user has
// completed the sign-in and sync flow then explicitly turned off the Sync
// setting.
TEST_F(SettingsTableViewControllerTest, TurnsSyncOffAfterFirstSetup) {
  ON_CALL(*sync_service_mock_->GetMockUserSettings(), IsFirstSetupComplete())
      .WillByDefault(Return(true));
  ON_CALL(*sync_setup_service_mock_, CanSyncFeatureStart())
      .WillByDefault(Return(false));
  auth_service_->SignIn(fake_identity_);

  CreateController();
  CheckController();

  NSArray* account_items = [controller().tableViewModel
      itemsInSectionWithIdentifier:SettingsSectionIdentifier::
                                       SettingsSectionIdentifierAccount];
  ASSERT_EQ(3U, account_items.count);

  TableViewDetailIconItem* sync_item =
      static_cast<TableViewDetailIconItem*>(account_items[1]);
  ASSERT_NSEQ(l10n_util::GetNSString(IDS_IOS_GOOGLE_SYNC_SETTINGS_TITLE),
              sync_item.text);
  ASSERT_NSEQ(nil, sync_item.detailText);
  // Check that there is no sign-in promo when there is a sync error.
  ASSERT_FALSE([controller().tableViewModel
      hasSectionForSectionIdentifier:SettingsSectionIdentifier::
                                         SettingsSectionIdentifierSignIn]);
}

// Verifies that the Sync icon displays the off state (and no detail text) when
// the user has completed the sign-in and sync flow then explicitly turned off
// all data types in the Sync settings.
// This case can only happen for pre-MICE users who migrated with MICE.
TEST_F(SettingsTableViewControllerTest,
       DisablesAllSyncSettingsAfterFirstSetup) {
  ON_CALL(*sync_service_mock_->GetMockUserSettings(), GetSelectedTypes())
      .WillByDefault(Return(syncer::UserSelectableTypeSet()));
  ON_CALL(*sync_service_mock_->GetMockUserSettings(), IsFirstSetupComplete())
      .WillByDefault(Return(true));
  ON_CALL(*sync_setup_service_mock_, CanSyncFeatureStart())
      .WillByDefault(Return(true));
  auth_service_->SignIn(fake_identity_);

  CreateController();
  CheckController();

  NSArray* account_items = [controller().tableViewModel
      itemsInSectionWithIdentifier:SettingsSectionIdentifier::
                                       SettingsSectionIdentifierAccount];
  ASSERT_EQ(3U, account_items.count);

  TableViewDetailIconItem* sync_item =
      static_cast<TableViewDetailIconItem*>(account_items[1]);
  ASSERT_NSEQ(l10n_util::GetNSString(IDS_IOS_GOOGLE_SYNC_SETTINGS_TITLE),
              sync_item.text);
  ASSERT_EQ(nil, sync_item.detailText);
}

// Verifies that the sign-in setting row is removed if sign-in is disabled
// through the "Allow Chrome Sign-in" option.
TEST_F(SettingsTableViewControllerTest, SigninDisabled) {
  chrome_browser_state_->GetPrefs()->SetBoolean(prefs::kSigninAllowed, false);
  CreateController();
  CheckController();

  ASSERT_FALSE([controller().tableViewModel
      hasSectionForSectionIdentifier:SettingsSectionIdentifier::
                                         SettingsSectionIdentifierSignIn]);
}

// Verifies that the Sync icon displays the off state (with OFF in detail text)
// when the user has not agreed on sync. This case is possible when using
// web sign-in.
TEST_F(SettingsTableViewControllerTest, SyncSetupNotComplete) {
  ON_CALL(*sync_service_mock_->GetMockUserSettings(), IsFirstSetupComplete())
      .WillByDefault(Return(false));
  auth_service_->SignIn(fake_identity_);

  CreateController();
  CheckController();

  NSArray* account_items = [controller().tableViewModel
      itemsInSectionWithIdentifier:SettingsSectionIdentifier::
                                       SettingsSectionIdentifierAccount];
  ASSERT_EQ(3U, account_items.count);

  TableViewDetailIconItem* sync_item =
      static_cast<TableViewDetailIconItem*>(account_items[1]);
  ASSERT_NSEQ(l10n_util::GetNSString(IDS_IOS_GOOGLE_SYNC_SETTINGS_TITLE),
              sync_item.text);
  ASSERT_NSEQ(l10n_util::GetNSString(IDS_IOS_SETTING_OFF),
              sync_item.detailText);
}

// Verifies that the sign-in setting item is replaced by the managed sign-in
// item if sign-in is disabled by policy.
TEST_F(SettingsTableViewControllerTest, SigninDisabledByPolicy) {
  AddSigninDisabledEnterprisePolicy();
  GetLocalState()->SetInteger(prefs::kBrowserSigninPolicy,
                              static_cast<int>(BrowserSigninMode::kDisabled));
  CreateController();
  CheckController();

  NSArray* signin_items = [controller().tableViewModel
      itemsInSectionWithIdentifier:SettingsSectionIdentifier::
                                       SettingsSectionIdentifierSignIn];
  ASSERT_EQ(1U, signin_items.count);

  TableViewInfoButtonItem* signin_item =
      static_cast<TableViewInfoButtonItem*>(signin_items[0]);
  ASSERT_NSEQ(signin_item.text,
              l10n_util::GetNSString(IDS_IOS_SIGN_IN_TO_CHROME_SETTING_TITLE));
  ASSERT_NSEQ(signin_item.statusText,
              l10n_util::GetNSString(IDS_IOS_SETTING_OFF));
}
