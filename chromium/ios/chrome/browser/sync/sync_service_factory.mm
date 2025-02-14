// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/sync/sync_service_factory.h"

#import <utility>

#import "base/bind.h"
#import "base/no_destructor.h"
#import "base/time/time.h"
#import "components/autofill/core/browser/personal_data_manager.h"
#import "components/keyed_service/ios/browser_state_dependency_manager.h"
#import "components/network_time/network_time_tracker.h"
#import "components/sync/base/command_line_switches.h"
#import "components/sync/base/sync_util.h"
#import "components/sync/driver/sync_service.h"
#import "components/sync/driver/sync_service_impl.h"
#import "ios/chrome/browser/application_context/application_context.h"
#import "ios/chrome/browser/autofill/personal_data_manager_factory.h"
#import "ios/chrome/browser/bookmarks/bookmark_model_factory.h"
#import "ios/chrome/browser/bookmarks/bookmark_sync_service_factory.h"
#import "ios/chrome/browser/browser_state/chrome_browser_state.h"
#import "ios/chrome/browser/consent_auditor/consent_auditor_factory.h"
#import "ios/chrome/browser/favicon/favicon_service_factory.h"
#import "ios/chrome/browser/gcm/ios_chrome_gcm_profile_service_factory.h"
#import "ios/chrome/browser/history/history_service_factory.h"
#import "ios/chrome/browser/passwords/ios_chrome_password_store_factory.h"
#import "ios/chrome/browser/reading_list/reading_list_model_factory.h"
#import "ios/chrome/browser/search_engines/template_url_service_factory.h"
#import "ios/chrome/browser/signin/about_signin_internals_factory.h"
#import "ios/chrome/browser/signin/chrome_account_manager_service_factory.h"
#import "ios/chrome/browser/signin/identity_manager_factory.h"
#import "ios/chrome/browser/signin/trusted_vault_client_backend_factory.h"
#import "ios/chrome/browser/sync/device_info_sync_service_factory.h"
#import "ios/chrome/browser/sync/ios_chrome_sync_client.h"
#import "ios/chrome/browser/sync/ios_user_event_service_factory.h"
#import "ios/chrome/browser/sync/model_type_store_service_factory.h"
#import "ios/chrome/browser/sync/session_sync_service_factory.h"
#import "ios/chrome/browser/sync/sync_invalidations_service_factory.h"
#import "ios/chrome/browser/undo/bookmark_undo_service_factory.h"
#import "ios/chrome/browser/webdata_services/web_data_service_factory.h"
#import "ios/chrome/common/channel_info.h"
#import "ios/web/public/thread/web_task_traits.h"
#import "ios/web/public/thread/web_thread.h"
#import "services/network/public/cpp/shared_url_loader_factory.h"
#import "url/gurl.h"

#include "app/vivaldi_apptools.h"
#include "ios/sync/vivaldi_sync_service_factory.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

// static
SyncServiceFactory* SyncServiceFactory::GetInstance() {
#if defined(VIVALDI_BUILD)
  if (vivaldi::IsVivaldiRunning() || vivaldi::ForcedVivaldiRunning())
    return vivaldi::VivaldiSyncServiceFactory::GetInstance();
#endif
  static base::NoDestructor<SyncServiceFactory> instance;
  return instance.get();
}

// static
syncer::SyncService* SyncServiceFactory::GetForBrowserState(
    ChromeBrowserState* browser_state) {
  if (!syncer::IsSyncAllowedByFlag())
    return nullptr;

  return static_cast<syncer::SyncService*>(
      GetInstance()->GetServiceForBrowserState(browser_state, true));
}

// static
syncer::SyncService* SyncServiceFactory::GetForBrowserStateIfExists(
    ChromeBrowserState* browser_state) {
  if (!syncer::IsSyncAllowedByFlag())
    return nullptr;

  return static_cast<syncer::SyncService*>(
      GetInstance()->GetServiceForBrowserState(browser_state, false));
}

// static
syncer::SyncServiceImpl*
SyncServiceFactory::GetAsSyncServiceImplForBrowserStateForTesting(
    ChromeBrowserState* browser_state) {
  return static_cast<syncer::SyncServiceImpl*>(
      GetForBrowserState(browser_state));
}

SyncServiceFactory::SyncServiceFactory()
    : BrowserStateKeyedServiceFactory(
          "SyncService",
          BrowserStateDependencyManager::GetInstance()) {
  // The SyncService depends on various SyncableServices being around
  // when it is shut down.  Specify those dependencies here to build the proper
  // destruction order.
  DependsOn(autofill::PersonalDataManagerFactory::GetInstance());
  DependsOn(ChromeAccountManagerServiceFactory::GetInstance());
  DependsOn(ConsentAuditorFactory::GetInstance());
  DependsOn(DeviceInfoSyncServiceFactory::GetInstance());
  DependsOn(ios::AboutSigninInternalsFactory::GetInstance());
  DependsOn(ios::BookmarkModelFactory::GetInstance());
  DependsOn(ios::BookmarkSyncServiceFactory::GetInstance());
  DependsOn(ios::BookmarkUndoServiceFactory::GetInstance());
  DependsOn(ios::FaviconServiceFactory::GetInstance());
  DependsOn(ios::HistoryServiceFactory::GetInstance());
  DependsOn(ios::TemplateURLServiceFactory::GetInstance());
  DependsOn(ios::WebDataServiceFactory::GetInstance());
  DependsOn(IdentityManagerFactory::GetInstance());
  DependsOn(IOSChromeGCMProfileServiceFactory::GetInstance());
  DependsOn(IOSChromePasswordStoreFactory::GetInstance());
  DependsOn(IOSUserEventServiceFactory::GetInstance());
  DependsOn(ModelTypeStoreServiceFactory::GetInstance());
  DependsOn(ReadingListModelFactory::GetInstance());
  DependsOn(SessionSyncServiceFactory::GetInstance());
  DependsOn(SyncInvalidationsServiceFactory::GetInstance());
  DependsOn(TrustedVaultClientBackendFactory::GetInstance());
}

SyncServiceFactory::~SyncServiceFactory() {}

std::unique_ptr<KeyedService> SyncServiceFactory::BuildServiceInstanceFor(
    web::BrowserState* context) const {
  ChromeBrowserState* browser_state =
      ChromeBrowserState::FromBrowserState(context);

  DCHECK(!browser_state->IsOffTheRecord());

  // Always create the GCMProfileService instance such that we can listen to
  // the profile notifications and purge the GCM store when the profile is
  // being signed out.
  IOSChromeGCMProfileServiceFactory::GetForBrowserState(browser_state);

  // TODO(crbug.com/171406): Change AboutSigninInternalsFactory to load on
  // startup once bug has been fixed.
  ios::AboutSigninInternalsFactory::GetForBrowserState(browser_state);

  syncer::SyncServiceImpl::InitParams init_params;
  // On non-iOS platforms, there are some "uninteresting" types of profiles such
  // as guest or system profiles. There's no such thing on iOS.
  init_params.is_regular_profile_for_uma = true;
  init_params.identity_manager =
      IdentityManagerFactory::GetForBrowserState(browser_state);
  init_params.start_behavior = syncer::SyncServiceImpl::MANUAL_START;
  init_params.sync_client =
      std::make_unique<IOSChromeSyncClient>(browser_state);
  init_params.url_loader_factory = browser_state->GetSharedURLLoaderFactory();
  init_params.network_connection_tracker =
      GetApplicationContext()->GetNetworkConnectionTracker();
  init_params.channel = ::GetChannel();
  init_params.debug_identifier = browser_state->GetDebugName();

  auto sync_service =
      std::make_unique<syncer::SyncServiceImpl>(std::move(init_params));
  sync_service->Initialize();

  // Hook `sync_service` into PersonalDataManager (a circular dependency).
  autofill::PersonalDataManager* pdm =
      autofill::PersonalDataManagerFactory::GetForBrowserState(browser_state);
  pdm->OnSyncServiceInitialized(sync_service.get());

  return sync_service;
}
