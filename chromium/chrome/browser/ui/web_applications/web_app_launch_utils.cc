// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/web_applications/web_app_launch_utils.h"

#include <atomic>
#include <memory>

#include "base/check.h"
#include "base/check_op.h"
#include "base/feature_list.h"
#include "base/memory/raw_ptr.h"
#include "base/metrics/histogram_base.h"
#include "base/metrics/histogram_macros.h"
#include "base/metrics/histogram_macros_internal.h"
#include "base/one_shot_event.h"
#include "base/time/time.h"
#include "base/trace_event/typed_macros.h"
#include "base/tracing/protos/chrome_track_event.pbzero.h"
#include "build/buildflag.h"
#include "build/chromeos_buildflags.h"
#include "chrome/browser/app_mode/app_mode_utils.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/profiles/profiles_state.h"
#include "chrome/browser/sessions/app_session_service.h"
#include "chrome/browser/sessions/app_session_service_factory.h"
#include "chrome/browser/sessions/session_service_base.h"
#include "chrome/browser/sessions/session_service_lookup.h"
#include "chrome/browser/ui/browser.h"
#include "chrome/browser/ui/browser_commands.h"
#include "chrome/browser/ui/browser_finder.h"
#include "chrome/browser/ui/browser_list.h"
#include "chrome/browser/ui/browser_navigator.h"
#include "chrome/browser/ui/browser_navigator_params.h"
#include "chrome/browser/ui/browser_tabstrip.h"
#include "chrome/browser/ui/browser_window.h"
#include "chrome/browser/ui/tabs/tab_enums.h"
#include "chrome/browser/ui/tabs/tab_strip_model.h"
#include "chrome/browser/ui/web_applications/app_browser_controller.h"
#include "chrome/browser/ui/web_applications/web_app_browser_controller.h"
#include "chrome/browser/ui/web_applications/web_app_tabbed_utils.h"
#include "chrome/browser/web_applications/web_app_constants.h"
#include "chrome/browser/web_applications/web_app_helpers.h"
#include "chrome/browser/web_applications/web_app_install_utils.h"
#include "chrome/browser/web_applications/web_app_provider.h"
#include "chrome/browser/web_applications/web_app_registrar.h"
#include "chrome/browser/web_applications/web_app_sync_bridge.h"
#include "chrome/browser/web_applications/web_app_tab_helper.h"
#include "chrome/browser/web_applications/web_app_utils.h"
#include "chrome/common/chrome_features.h"
#include "components/services/app_service/public/cpp/app_launch_util.h"
#include "components/site_engagement/content/site_engagement_service.h"
#include "content/public/browser/navigation_controller.h"
#include "content/public/browser/navigation_entry.h"
#include "content/public/browser/render_frame_host.h"
#include "content/public/browser/web_contents.h"
#include "extensions/buildflags/buildflags.h"
#include "extensions/common/constants.h"
#include "third_party/blink/public/common/renderer_preferences/renderer_preferences.h"
#include "ui/base/page_transition_types.h"
#include "ui/base/ui_base_types.h"
#include "ui/base/window_open_disposition.h"
#include "url/gurl.h"

#if BUILDFLAG(ENABLE_EXTENSIONS)
#include "chrome/browser/ui/extensions/hosted_app_browser_controller.h"
#include "extensions/browser/extension_registry.h"
#include "extensions/common/extension.h"
#endif

#if BUILDFLAG(IS_CHROMEOS_ASH)
#include "chrome/browser/ash/app_mode/web_app/web_kiosk_browser_controller_ash.h"
#include "chrome/browser/ash/system_web_apps/system_web_app_manager.h"
#include "chrome/browser/ash/system_web_apps/types/system_web_app_delegate.h"
#include "chrome/browser/ui/ash/system_web_apps/system_web_app_ui_utils.h"
#include "components/user_manager/user_manager.h"
#endif  // BUILDFLAG(IS_CHROMEOS_ASH)

#include "app/vivaldi_apptools.h"

namespace web_app {

namespace {

ui::WindowShowState DetermineWindowShowState() {
  if (chrome::IsRunningInForcedAppMode())
    return ui::SHOW_STATE_FULLSCREEN;

  return ui::SHOW_STATE_DEFAULT;
}

Browser* ReparentWebContentsIntoAppBrowser(content::WebContents* contents,
                                           Browser* target_browser,
                                           const AppId& app_id,
                                           bool as_pinned_home_tab) {
  DCHECK(target_browser->is_type_app());
  Browser* source_browser = chrome::FindBrowserWithWebContents(contents);

  if (vivaldi::IsVivaldiRunning() && !target_browser->is_vivaldi()) {
    // If this is a non-vivaldi window, we cannot move the tab over due
    // to WebContentsChildFrame vs WebContentsView conflict.
    content::WebContents::CreateParams params(target_browser->profile());
    std::unique_ptr<content::WebContents> source_contents(
        content::WebContents::Create(params));

    source_contents->GetController().CopyStateFrom(&contents->GetController(),
                                                   true);

    target_browser->tab_strip_model()->AppendWebContents(
        std::move(source_contents), true);
    target_browser->window()->Show();

#if BUILDFLAG(ENABLE_SESSION_SERVICE)
    // The app window will be registered correctly, however the tab will not
    // be correctly tracked. We need to do a reset to get the tab correctly
    // tracked by the app service.
    AppSessionService* app_service =
        AppSessionServiceFactory::GetForProfile(target_browser->profile());
    app_service->ResetFromCurrentBrowsers();
#endif

    return target_browser;
  }
  // In a reparent, the owning session service needs to be told it's tab
  // has been removed, otherwise it will reopen the tab on restoration.
  SessionServiceBase* service =
      GetAppropriateSessionServiceForProfile(source_browser);
  service->TabClosing(contents);

  TabStripModel* source_tabstrip = source_browser->tab_strip_model();
  TabStripModel* target_tabstrip = target_browser->tab_strip_model();

  // Avoid causing the existing browser window to close if this is the last tab
  // remaining.
  if (source_tabstrip->count() == 1)
    chrome::NewTab(source_browser);

  if (as_pinned_home_tab) {
    if (HasPinnedHomeTab(target_tabstrip)) {
      // Insert the web contents into the pinned home tab and delete the
      // existing home tab.
      target_tabstrip->InsertWebContentsAt(
          /*index=*/0,
          source_tabstrip->DetachWebContentsAtForInsertion(
              source_tabstrip->GetIndexOfWebContents(contents)),
          (AddTabTypes::ADD_INHERIT_OPENER | AddTabTypes::ADD_ACTIVE |
           AddTabTypes::ADD_PINNED));
      target_tabstrip->DetachAndDeleteWebContentsAt(1);
    } else {
      target_tabstrip->InsertWebContentsAt(
          /*index=*/0,
          source_tabstrip->DetachWebContentsAtForInsertion(
              source_tabstrip->GetIndexOfWebContents(contents)),
          (AddTabTypes::ADD_INHERIT_OPENER | AddTabTypes::ADD_ACTIVE |
           AddTabTypes::ADD_PINNED));
    }
    SetWebContentsIsPinnedHomeTab(target_tabstrip->GetWebContentsAt(0));
  } else {
    MaybeAddPinnedHomeTab(target_browser, app_id);
    target_tabstrip->AppendWebContents(
        source_tabstrip->DetachWebContentsAtForInsertion(
            source_tabstrip->GetIndexOfWebContents(contents)),
        true);
  }
  target_browser->window()->Show();

  // The app window will be registered correctly, however the tab will not
  // be correctly tracked. We need to do a reset to get the tab correctly
  // tracked by the app service.
  AppSessionService* app_service =
      AppSessionServiceFactory::GetForProfile(target_browser->profile());
  app_service->ResetFromCurrentBrowsers();

  return target_browser;
}

#if BUILDFLAG(IS_CHROMEOS)
std::unique_ptr<AppBrowserController> CreateWebKioskBrowserController(
    Browser* browser,
    WebAppProvider* provider,
    const AppId& app_id) {
#if BUILDFLAG(IS_CHROMEOS_ASH)
  return std::make_unique<ash::WebKioskBrowserControllerAsh>(*provider, browser,
                                                             app_id);
#else
  // TODO(b/242023891): Add web Kiosk browser controller for Lacros.
  return nullptr;
#endif  // BUILDFLAG(IS_CHROMEOS_ASH)
}
#endif  // BUILDFLAG(IS_CHROMEOS)

#if BUILDFLAG(IS_CHROMEOS_ASH)
const ash::SystemWebAppDelegate* GetSystemWebAppDelegate(Browser* browser,
                                                         const AppId& app_id) {
  auto system_app_type =
      ash::GetSystemWebAppTypeForAppId(browser->profile(), app_id);
  if (system_app_type) {
    return ash::SystemWebAppManager::GetForLocalAppsUnchecked(
               browser->profile())
        ->GetSystemApp(*system_app_type);
  }
  return nullptr;
}
#endif  // BUILDFLAG(IS_CHROMEOS_ASH)

std::unique_ptr<AppBrowserController> CreateWebAppBrowserController(
    Browser* browser,
    WebAppProvider* provider,
    const AppId& app_id) {
  bool should_have_tab_strip_for_swa = false;
#if BUILDFLAG(IS_CHROMEOS_ASH)
  const ash::SystemWebAppDelegate* system_app =
      GetSystemWebAppDelegate(browser, app_id);
  should_have_tab_strip_for_swa =
      system_app && system_app->ShouldHaveTabStrip();
#endif  // BUILDFLAG(IS_CHROMEOS_ASH)
  const bool has_tab_strip =
      !browser->is_type_app_popup() &&
      (should_have_tab_strip_for_swa ||
       provider->registrar().IsTabbedWindowModeEnabled(app_id));
  return std::make_unique<WebAppBrowserController>(*provider, browser, app_id,
#if BUILDFLAG(IS_CHROMEOS_ASH)
                                                   system_app,
#endif  // BUILDFLAG(IS_CHROMEOS_ASH)
                                                   has_tab_strip);
}

std::unique_ptr<AppBrowserController> MaybeCreateHostedAppBrowserController(
    Browser* browser,
    const AppId& app_id) {
#if BUILDFLAG(ENABLE_EXTENSIONS)
  const extensions::Extension* extension =
      extensions::ExtensionRegistry::Get(browser->profile())
          ->GetExtensionById(app_id, extensions::ExtensionRegistry::EVERYTHING);
  if (extension && extension->is_hosted_app()) {
    return std::make_unique<extensions::HostedAppBrowserController>(browser);
  }
#endif  // BUILDFLAG(ENABLE_EXTENSIONS)
  return nullptr;
}

}  // namespace

absl::optional<AppId> GetWebAppForActiveTab(Browser* browser) {
  WebAppProvider* provider = WebAppProvider::GetForWebApps(browser->profile());
  if (!provider)
    return absl::nullopt;

  content::WebContents* web_contents =
      browser->tab_strip_model()->GetActiveWebContents();
  if (!web_contents)
    return absl::nullopt;

  return provider->registrar().FindInstalledAppWithUrlInScope(
      web_contents->GetPrimaryMainFrame()->GetLastCommittedURL());
}

void PrunePreScopeNavigationHistory(const GURL& scope,
                                    content::WebContents* contents) {
  content::NavigationController& navigation_controller =
      contents->GetController();
  if (!navigation_controller.CanPruneAllButLastCommitted())
    return;

  int index = navigation_controller.GetEntryCount() - 1;
  while (index >= 0 &&
         IsInScope(navigation_controller.GetEntryAtIndex(index)->GetURL(),
                   scope)) {
    --index;
  }

  while (index >= 0) {
    navigation_controller.RemoveEntryAtIndex(index);
    --index;
  }
}

Browser* ReparentWebAppForActiveTab(Browser* browser) {
  absl::optional<AppId> app_id = GetWebAppForActiveTab(browser);
  if (!app_id)
    return nullptr;
  return ReparentWebContentsIntoAppBrowser(
      browser->tab_strip_model()->GetActiveWebContents(), *app_id);
}

Browser* ReparentWebContentsIntoAppBrowser(content::WebContents* contents,
                                           const AppId& app_id) {
  Profile* profile = Profile::FromBrowserContext(contents->GetBrowserContext());
  // Incognito tabs reparent correctly, but remain incognito without any
  // indication to the user, so disallow it.
  DCHECK(!profile->IsOffTheRecord());

  // Clear navigation history that occurred before the user most recently
  // entered the app's scope. The minimal-ui Back button will be initially
  // disabled if the previous page was outside scope. Packaged apps are not
  // affected.
  WebAppRegistrar& registrar =
      WebAppProvider::GetForWebApps(profile)->registrar();
  if (registrar.IsInstalled(app_id)) {
    absl::optional<GURL> app_scope = registrar.GetAppScope(app_id);
    if (!app_scope)
      app_scope = registrar.GetAppStartUrl(app_id).GetWithoutFilename();

    PrunePreScopeNavigationHistory(*app_scope, contents);
  }

  auto launch_url = contents->GetLastCommittedURL();
  UpdateLaunchStats(contents, app_id, launch_url);
  RecordLaunchMetrics(app_id, apps::LaunchContainer::kLaunchContainerWindow,
                      extensions::AppLaunchSource::kSourceReparenting,
                      launch_url, contents);

  bool as_pinned_home_tab = IsPinnedHomeTabUrl(registrar, app_id, launch_url);

  if (registrar.IsTabbedWindowModeEnabled(app_id)) {
    for (Browser* browser : *BrowserList::GetInstance()) {
      if (AppBrowserController::IsForWebApp(browser, app_id))
        return ReparentWebContentsIntoAppBrowser(contents, browser, app_id,
                                                 as_pinned_home_tab);
    }
  }

  Browser::CreateParams browser_params(Browser::CreateParams::CreateForApp(
          GenerateApplicationNameFromAppId(app_id), true /* trusted_source */,
          gfx::Rect(), profile, true /* user_gesture */));

  // We're not using a Vivaldi popup for PWAs as we need full functionality.
  browser_params.is_vivaldi = false;

  return ReparentWebContentsIntoAppBrowser(
      contents,
      Browser::Create(browser_params),
      app_id, as_pinned_home_tab);
}

void SetWebContentsActingAsApp(content::WebContents* contents,
                               const AppId& app_id) {
  auto* helper = WebAppTabHelper::FromWebContents(contents);
  helper->SetAppId(app_id);
  helper->set_acting_as_app(true);
}

void SetWebContentsIsPinnedHomeTab(content::WebContents* contents) {
  auto* helper = WebAppTabHelper::FromWebContents(contents);
  helper->set_is_pinned_home_tab(true);
}

void SetAppPrefsForWebContents(content::WebContents* web_contents) {
  web_contents->GetMutableRendererPrefs()->can_accept_load_drops = false;
  web_contents->SyncRendererPrefs();

  web_contents->NotifyPreferencesChanged();
}

void ClearAppPrefsForWebContents(content::WebContents* web_contents) {
  web_contents->GetMutableRendererPrefs()->can_accept_load_drops = true;
  web_contents->SyncRendererPrefs();

  web_contents->NotifyPreferencesChanged();
}

std::unique_ptr<AppBrowserController> MaybeCreateAppBrowserController(
    Browser* browser) {
  std::unique_ptr<AppBrowserController> controller;
  const AppId app_id = GetAppIdFromApplicationName(browser->app_name());
  auto* const provider =
      WebAppProvider::GetForLocalAppsUnchecked(browser->profile());
  if (provider && provider->registrar().IsInstalled(app_id)) {
#if BUILDFLAG(IS_CHROMEOS)
    if (profiles::IsKioskSession() &&
        base::FeatureList::IsEnabled(features::kKioskEnableAppService)) {
      controller = CreateWebKioskBrowserController(browser, provider, app_id);
    } else {
      controller = CreateWebAppBrowserController(browser, provider, app_id);
    }
#else
    controller = CreateWebAppBrowserController(browser, provider, app_id);
#endif  // BUILDFLAG(IS_CHROMEOS)
  } else {
    controller = MaybeCreateHostedAppBrowserController(browser, app_id);
  }
  if (controller)
    controller->Init();
  return controller;
}

void MaybeAddPinnedHomeTab(Browser* browser, const std::string& app_id) {
  WebAppRegistrar& registrar =
      WebAppProvider::GetForLocalAppsUnchecked(browser->profile())->registrar();
  absl::optional<GURL> pinned_home_tab_url =
      registrar.GetAppPinnedHomeTabUrl(app_id);

  if (registrar.IsTabbedWindowModeEnabled(app_id) &&
      !HasPinnedHomeTab(browser->tab_strip_model()) &&
      pinned_home_tab_url.has_value()) {
    NavigateParams home_tab_nav_params(browser, pinned_home_tab_url.value(),
                                       ui::PAGE_TRANSITION_AUTO_BOOKMARK);
    home_tab_nav_params.disposition = WindowOpenDisposition::NEW_BACKGROUND_TAB;
    home_tab_nav_params.tabstrip_add_types |= AddTabTypes::ADD_PINNED;
    Navigate(&home_tab_nav_params);

    content::WebContents* const web_contents =
        home_tab_nav_params.navigated_or_inserted_contents;

    if (web_contents) {
      SetWebContentsIsPinnedHomeTab(web_contents);
    }
  }
}

Browser* CreateWebApplicationWindow(Profile* profile,
                                    const std::string& app_id,
                                    WindowOpenDisposition disposition,
                                    int32_t restore_id,
                                    bool omit_from_session_restore,
                                    bool can_resize,
                                    bool can_maximize,
                                    const gfx::Rect initial_bounds) {
  std::string app_name = GenerateApplicationNameFromAppId(app_id);
  Browser::CreateParams browser_params =
      disposition == WindowOpenDisposition::NEW_POPUP
          ? Browser::CreateParams::CreateForAppPopup(
                app_name, /*trusted_source=*/true, initial_bounds, profile,
                /*user_gesture=*/true)
          : Browser::CreateParams::CreateForApp(
                app_name, /*trusted_source=*/true, initial_bounds, profile,
                /*user_gesture=*/true);
  browser_params.initial_show_state = DetermineWindowShowState();
#if BUILDFLAG(IS_CHROMEOS_ASH)
  browser_params.restore_id = restore_id;
#endif
  browser_params.omit_from_session_restore = omit_from_session_restore;
  browser_params.can_resize = can_resize;
  browser_params.can_maximize = can_maximize;
  browser_params.are_tab_groups_enabled = false;
  Browser* browser = Browser::Create(browser_params);
  MaybeAddPinnedHomeTab(browser, app_id);
  return browser;
}

content::WebContents* NavigateWebApplicationWindow(
    Browser* browser,
    const std::string& app_id,
    const GURL& url,
    WindowOpenDisposition disposition) {
  NavigateParams nav_params(browser, url, ui::PAGE_TRANSITION_AUTO_BOOKMARK);
  nav_params.disposition = disposition;
  return NavigateWebAppUsingParams(app_id, nav_params);
}

content::WebContents* NavigateWebAppUsingParams(const std::string& app_id,
                                                NavigateParams& nav_params) {
  WebAppRegistrar& registrar =
      WebAppProvider::GetForLocalAppsUnchecked(nav_params.browser->profile())
          ->registrar();

  if (IsPinnedHomeTabUrl(registrar, app_id, nav_params.url)) {
    // Navigations to the home tab URL in tabbed apps should happen in the home
    // tab.
    nav_params.browser->tab_strip_model()->ActivateTabAt(0);
    content::WebContents* home_tab_web_contents =
        nav_params.browser->tab_strip_model()->GetWebContentsAt(0);
    GURL previous_home_tab_url = home_tab_web_contents->GetLastCommittedURL();
    if (previous_home_tab_url == nav_params.url) {
      // URL is identical so no need for the navigation.
      return home_tab_web_contents;
    }
    nav_params.disposition = WindowOpenDisposition::CURRENT_TAB;
  }

#if BUILDFLAG(IS_CHROMEOS_ASH)
  Browser* browser = nav_params.browser;
  const absl::optional<ash::SystemWebAppType> capturing_system_app_type =
      ash::GetCapturingSystemAppForURL(browser->profile(), nav_params.url);
  // TODO(crbug.com/1201820): This block creates conditions where Navigate()
  // returns early and causes a crash. Fail gracefully instead. Further
  // debugging state will be implemented via Chrometto UMA traces.
  if (capturing_system_app_type &&
      (!browser ||
       !IsBrowserForSystemWebApp(browser, capturing_system_app_type.value()))) {
    auto* user_manager = user_manager::UserManager::Get();
    bool is_kiosk = user_manager && user_manager->IsLoggedInAsAnyKioskApp();
    AppBrowserController* app_controller = browser->app_controller();
    WebAppProvider* web_app_provider =
        WebAppProvider::GetForLocalAppsUnchecked(browser->profile());
    DCHECK(web_app_provider);
    ash::SystemWebAppManager* swa_manager =
        ash::SystemWebAppManager::GetForLocalAppsUnchecked(browser->profile());
    DCHECK(swa_manager);

    TRACE_EVENT_INSTANT(
        "system_apps", "BadNavigate", [&](perfetto::EventContext ctx) {
          auto* bad_navigate =
              ctx.event<perfetto::protos::pbzero::ChromeTrackEvent>()
                  ->set_chrome_web_app_bad_navigate();
          bad_navigate->set_is_kiosk(is_kiosk);
          bad_navigate->set_has_hosted_app_controller(!!app_controller);
          bad_navigate->set_app_name(browser->app_name());
          if (app_controller && app_controller->system_app()) {
            bad_navigate->set_system_app_type(
                static_cast<uint32_t>(app_controller->system_app()->GetType()));
          }
          bad_navigate->set_web_app_provider_registry_ready(
              web_app_provider->on_registry_ready().is_signaled());
          bad_navigate->set_system_web_app_manager_synchronized(
              swa_manager->on_apps_synchronized().is_signaled());
        });
    UMA_HISTOGRAM_ENUMERATION("WebApp.SystemApps.BadNavigate.Type",
                              capturing_system_app_type.value());
    return nullptr;
  }
#endif  // BUILDFLAG(IS_CHROMEOS_ASH)

  Navigate(&nav_params);

  content::WebContents* const web_contents =
      nav_params.navigated_or_inserted_contents;

  if (web_contents) {
    SetWebContentsActingAsApp(web_contents, app_id);
    SetAppPrefsForWebContents(web_contents);
  }

  return web_contents;
}

void RecordAppWindowLaunchMetric(Profile* profile, const std::string& app_id) {
  WebAppProvider* provider = WebAppProvider::GetForLocalAppsUnchecked(profile);
  if (!provider)
    return;

  DisplayMode display =
      provider->registrar().GetEffectiveDisplayModeFromManifest(app_id);
  if (display == DisplayMode::kUndefined)
    return;

  DCHECK_LT(DisplayMode::kUndefined, display);
  DCHECK_LE(display, DisplayMode::kMaxValue);
  UMA_HISTOGRAM_ENUMERATION("Launch.WebAppDisplayMode", display);
}

void RecordLaunchMetrics(const AppId& app_id,
                         apps::LaunchContainer container,
                         extensions::AppLaunchSource launch_source,
                         const GURL& launch_url,
                         content::WebContents* web_contents) {
  Profile* profile =
      Profile::FromBrowserContext(web_contents->GetBrowserContext());

#if BUILDFLAG(IS_CHROMEOS_ASH)
  // System web apps have different launch paths compared with web apps, and
  // those paths aren't configurable. So their launch metrics shouldn't be
  // reported to avoid skewing web app metrics.
  DCHECK(!ash::GetSystemWebAppTypeForAppId(profile, app_id))
      << "System web apps shouldn't be included in web app launch metrics";
#endif  // BUILDFLAG(IS_CHROMEOS_ASH)

  if (container == apps::LaunchContainer::kLaunchContainerWindow)
    RecordAppWindowLaunchMetric(profile, app_id);

  // TODO(crbug.com/1014328): Populate WebApp metrics instead of Extensions.
  UMA_HISTOGRAM_ENUMERATION("Extensions.BookmarkAppLaunchSource",
                            launch_source);
  UMA_HISTOGRAM_ENUMERATION("Extensions.BookmarkAppLaunchContainer", container);
}

void UpdateLaunchStats(content::WebContents* web_contents,
                       const AppId& app_id,
                       const GURL& launch_url) {
  Profile* profile =
      Profile::FromBrowserContext(web_contents->GetBrowserContext());

  WebAppProvider::GetForLocalAppsUnchecked(profile)
      ->sync_bridge()
      .SetAppLastLaunchTime(app_id, base::Time::Now());

#if BUILDFLAG(IS_CHROMEOS_ASH)
  if (ash::GetSystemWebAppTypeForAppId(profile, app_id)) {
    // System web apps doesn't use the rest of the stats.
    return;
  }
#endif  // BUILDFLAG(IS_CHROMEOS_ASH)

  // Update the launch time in the site engagement service. A recent web
  // app launch will provide an engagement boost to the origin.
  site_engagement::SiteEngagementService::Get(profile)
      ->SetLastShortcutLaunchTime(web_contents, launch_url);

  // Refresh the app banner added to homescreen event. The user may have
  // cleared their browsing data since installing the app, which removes the
  // event and will potentially permit a banner to be shown for the site.
  RecordAppBanner(web_contents, launch_url);
}

}  // namespace web_app
