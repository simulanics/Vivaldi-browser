// Copyright (c) 2017 Vivaldi Technologies AS. All rights reserved

#include "extensions/api/window/window_private_api.h"

#include <cstdint>
#include <memory>
#include <string>

#include "base/no_destructor.h"
#include "base/strings/stringprintf.h"
#include "build/build_config.h"
#include "chrome/browser/chrome_notification_types.h"
#include "chrome/browser/extensions/api/tabs/windows_util.h"
#include "chrome/browser/extensions/browser_extension_window_controller.h"
#include "chrome/browser/extensions/window_controller.h"
#include "chrome/browser/lifetime/application_lifetime.h"
#include "chrome/browser/ui/autofill/chrome_autofill_client.h"
#include "chrome/browser/ui/browser.h"
#include "chrome/browser/ui/browser_commands.h"
#include "chrome/browser/ui/browser_finder.h"
#include "chrome/browser/ui/browser_list.h"
#include "chrome/browser/ui/browser_list_observer.h"
#include "chrome/browser/ui/tabs/tab_strip_model_observer.h"
#include "chrome/browser/ui/window_sizer/window_sizer.h"
#include "components/sessions/content/session_tab_helper.h"
#include "content/browser/renderer_host/render_frame_host_impl.h"
#include "content/browser/web_contents/web_contents_impl.h"
#include "content/public/browser/notification_observer.h"
#include "content/public/browser/notification_registrar.h"
#include "content/public/browser/notification_service.h"
#include "content/public/browser/render_frame_host.h"
#include "content/public/browser/render_process_host.h"
#include "content/public/common/color_parser.h"
#include "extensions/common/image_util.h"
#include "mojo/public/cpp/bindings/callback_helpers.h"
#include "ui/base/ui_base_types.h"

#include "browser/vivaldi_browser_finder.h"
#include "extensions/api/extension_action_utils/extension_action_utils_api.h"
#include "extensions/api/tabs/tabs_private_api.h"
#include "extensions/api/zoom/zoom_api.h"
#include "extensions/tools/vivaldi_tools.h"
#include "ui/vivaldi_browser_window.h"
#include "ui/vivaldi_ui_utils.h"
#include "vivaldi/prefs/vivaldi_gen_prefs.h"

namespace extensions {

namespace vivaldi {
using vivaldi::window_private::WindowType;
WindowType ConvertToJSWindowType(VivaldiBrowserWindow::WindowType type) {
  switch (type) {
    case VivaldiBrowserWindow::WindowType::NORMAL:
      return WindowType::WINDOW_TYPE_NORMAL;
    case VivaldiBrowserWindow::WindowType::POPUP:
      return WindowType::WINDOW_TYPE_POPUP;
    case VivaldiBrowserWindow::WindowType::SETTINGS:
      return WindowType::WINDOW_TYPE_SETTINGS;
  }
  NOTREACHED();
  return WindowType::WINDOW_TYPE_NONE;
}

using vivaldi::window_private::WindowState;
WindowState ConvertToJSWindowState(ui::WindowShowState state) {
  switch (state) {
    case ui::SHOW_STATE_FULLSCREEN:
      return WindowState::WINDOW_STATE_FULLSCREEN;
    case ui::SHOW_STATE_MAXIMIZED:
      return WindowState::WINDOW_STATE_MAXIMIZED;
    case ui::SHOW_STATE_MINIMIZED:
      return WindowState::WINDOW_STATE_MINIMIZED;
    default:
      return WindowState::WINDOW_STATE_NORMAL;
  }
  NOTREACHED();
  return WindowState::WINDOW_STATE_NORMAL;
}

ui::WindowShowState ConvertToWindowShowState(
    vivaldi::window_private::WindowState state) {
  using vivaldi::window_private::WindowState;
  switch (state) {
    case WindowState::WINDOW_STATE_NORMAL:
      return ui::SHOW_STATE_NORMAL;
    case WindowState::WINDOW_STATE_MINIMIZED:
      return ui::SHOW_STATE_MINIMIZED;
    case WindowState::WINDOW_STATE_MAXIMIZED:
      return ui::SHOW_STATE_MAXIMIZED;
    case WindowState::WINDOW_STATE_FULLSCREEN:
      return ui::SHOW_STATE_FULLSCREEN;
    case WindowState::WINDOW_STATE_NONE:
      return ui::SHOW_STATE_DEFAULT;
  }
  NOTREACHED();
  return ui::SHOW_STATE_DEFAULT;
}
}  // namespace vivaldi

namespace {

class VivaldiBrowserObserver : public BrowserListObserver,
                               public TabStripModelObserver {
 public:
  VivaldiBrowserObserver();

  // This should not be called.
  ~VivaldiBrowserObserver() override { NOTREACHED(); }

  static VivaldiBrowserObserver& GetInstance();

  void WindowsForProfileClosing(Profile* profile);

  // If the browser is closing due to the profile closure, return the profile
  // index. Otherwise return SIZE_MAX.
  size_t FindClosingWindow(Browser* browser);

 private:
  friend class ::extensions::VivaldiWindowsAPI;

  // chrome::BrowserListObserver implementation
  void OnBrowserRemoved(Browser* browser) override;
  void OnBrowserAdded(Browser* browser) override;
  void OnBrowserSetLastActive(Browser* browser) override;

  // TabStripModelObserver implementation
  void TabChangedAt(content::WebContents* contents,
                    int index,
                    TabChangeType change_type) override;
  void OnTabStripModelChanged(
      TabStripModel* tab_strip_model,
      const TabStripModelChange& change,
      const TabStripSelectionChange& selection) override;

  // Used to track windows being closed by profiles being closed, they should
  // not have any confirmation dialogs.
  std::vector<Browser*> closing_windows_;
};

VivaldiBrowserObserver& VivaldiBrowserObserver::GetInstance() {
  static base::NoDestructor<VivaldiBrowserObserver> instance;
  return *instance;
}

VivaldiBrowserObserver::VivaldiBrowserObserver() {
  BrowserList::GetInstance()->AddObserver(this);
}

void VivaldiBrowserObserver::WindowsForProfileClosing(Profile* profile) {
  if (profile->IsGuestSession()) {
    // We don't care about guest windows.
    return;
  }
  for (auto* browser : *BrowserList::GetInstance()) {
    if (browser->profile()->GetOriginalProfile() ==
        profile->GetOriginalProfile()) {
      closing_windows_.push_back(browser);
    }
  }
}

size_t VivaldiBrowserObserver::FindClosingWindow(Browser* browser) {
  // STL is too verbose not to use a one-letter abbreviation.
  const auto& v = closing_windows_;
  auto i = std::find(v.begin(), v.end(), browser);
  return (i != v.end()) ? std::distance(v.begin(), i) : SIZE_MAX;
}

void VivaldiBrowserObserver::OnBrowserAdded(Browser* browser) {
  browser->tab_strip_model()->AddObserver(this);

  if (browser->is_vivaldi()) {
    ZoomAPI::AddZoomObserver(browser);
  }
}

void VivaldiBrowserObserver::OnBrowserRemoved(Browser* browser) {
  browser->tab_strip_model()->RemoveObserver(this);

  if (browser->is_vivaldi()) {
    ZoomAPI::RemoveZoomObserver(browser);
  }

  size_t i = FindClosingWindow(browser);
  if (i != SIZE_MAX) {
    closing_windows_.erase(closing_windows_.begin() + i);
  }

  int id = browser->session_id().id();
  ::vivaldi::BroadcastEvent(vivaldi::window_private::OnWindowClosed::kEventName,
                            vivaldi::window_private::OnWindowClosed::Create(id),
                            browser->profile());

  if (chrome::GetTotalBrowserCount() == 1) {
    for (Browser* browser_it : *BrowserList::GetInstance()) {
      // If this is the last normal window, close the settings
      // window so shutdown can progress normally.
      if (browser_it->is_vivaldi() &&
          static_cast<VivaldiBrowserWindow*>(browser_it->window())->type() ==
              VivaldiBrowserWindow::WindowType::SETTINGS) {
        browser_it->window()->Close();
        break;
      }
    }
  }
}

void VivaldiBrowserObserver::OnBrowserSetLastActive(Browser* browser) {
  TabsPrivateAPI::FromBrowserContext(browser->profile())
      ->NotifyTabSelectionChange(
          browser->tab_strip_model()->GetActiveWebContents());
}

void VivaldiBrowserObserver::TabChangedAt(content::WebContents* web_contents,
                                          int index,
                                          TabChangeType change_type) {
  // Ignore 'loading' and 'title' changes.
  if (change_type != TabChangeType::kAll)
    return;

  TabsPrivateAPI::FromBrowserContext(web_contents->GetBrowserContext())
      ->NotifyTabChange(web_contents);
}

void VivaldiBrowserObserver::OnTabStripModelChanged(
    TabStripModel* tab_strip_model,
    const TabStripModelChange& change,
    const TabStripSelectionChange& selection) {
  if (!selection.active_tab_changed() || !selection.new_contents)
    return;

  // Synthesize webcontents OnWebContentsLostFocus/OnWebContentsFocused
  if (selection.active_tab_changed()) {
    autofill::ChromeAutofillClient* old_fill_client =
        selection.old_contents
            ? autofill::ChromeAutofillClient::FromWebContents(
                  selection.old_contents)
            : nullptr;
    autofill::ChromeAutofillClient* new_fill_client =
        autofill::ChromeAutofillClient::FromWebContents(selection.new_contents);

    if (old_fill_client) {
      old_fill_client->OnWebContentsLostFocus(
          selection.old_contents->GetPrimaryMainFrame()->GetRenderWidgetHost());
    }
    new_fill_client->OnWebContentsFocused(
        selection.new_contents->GetPrimaryMainFrame()->GetRenderWidgetHost());
  }

  TabsPrivateAPI::FromBrowserContext(
      selection.new_contents->GetBrowserContext())
      ->NotifyTabSelectionChange(selection.new_contents);

  // Lookup the proper ExtensionActionUtil based on tab WebContents.
  extensions::ExtensionActionUtil* utils =
      extensions::ExtensionActionUtilFactory::GetForBrowserContext(
          selection.new_contents->GetBrowserContext());
  DCHECK(utils);
  if (!utils)
    return;
  utils->NotifyTabSelectionChange(selection.new_contents);
}

}  // namespace

// static
void VivaldiWindowsAPI::Init() {
  VivaldiBrowserObserver::GetInstance();
}

// static
void VivaldiWindowsAPI::WindowsForProfileClosing(Profile* profile) {
  VivaldiBrowserObserver::GetInstance().WindowsForProfileClosing(profile);
}

// static
bool VivaldiWindowsAPI::IsWindowClosingBecauseProfileClose(Browser* browser) {
  size_t i = VivaldiBrowserObserver::GetInstance().FindClosingWindow(browser);
  return i != SIZE_MAX;
}

ExtensionFunction::ResponseAction WindowPrivateCreateFunction::Run() {
  using vivaldi::window_private::Create::Params;
  namespace Results = vivaldi::window_private::Create::Results;

  std::unique_ptr<Params> params = Params::Create(args());
  EXTENSION_FUNCTION_VALIDATE(params.get());

  int min_width = 0;
  int min_height = 0;
  bool incognito = false;
  bool focused = true;
  std::string tab_url;
  std::string viv_ext_data;

  if (params->options.incognito.has_value()) {
    incognito = params->options.incognito.value();
  }
  if (params->options.focused.has_value()) {
    focused = params->options.focused.value();
  }
  if (params->options.tab_url.has_value()) {
    tab_url = params->options.tab_url.value();
  }
  if (params->options.viv_ext_data.has_value()) {
    viv_ext_data = params->options.viv_ext_data.value();
  }
  Profile* profile = Profile::FromBrowserContext(browser_context());
  if (incognito) {
    profile = profile->GetOffTheRecordProfile(
        Profile::OTRProfileID::PrimaryID(), true);
  } else {
    profile = profile->GetOriginalProfile();
  }

  gfx::Rect window_bounds;

  ui::WindowShowState ignored_show_state = ui::SHOW_STATE_DEFAULT;
  WindowSizer::GetBrowserWindowBoundsAndShowState(
      gfx::Rect(), nullptr, &window_bounds, &ignored_show_state);

  if (params->options.bounds) {
    if (params->options.bounds->top) {
      window_bounds.set_y(*params->options.bounds->top);
    }
    if (params->options.bounds->left) {
      window_bounds.set_x(*params->options.bounds->left);
    }
    if (params->options.bounds->width) {
      window_bounds.set_width(params->options.bounds->width);
    }
    if (params->options.bounds->height) {
      window_bounds.set_height(params->options.bounds->height);
    }

    if (params->options.bounds->min_width.has_value()) {
      min_width = params->options.bounds->min_width.value();
    }
    if (params->options.bounds->min_height.has_value()) {
      min_height = params->options.bounds->min_height.value();
    }
  }

  // App window specific parameters
  VivaldiBrowserWindowParams window_params;

  if (params->type ==
      vivaldi::window_private::WindowType::WINDOW_TYPE_SETTINGS) {
    window_params.settings_window = true;
  }
  window_params.focused = focused;
  if (params->options.window_decoration.has_value()) {
    window_params.native_decorations = params->options.window_decoration.value();
  } else {
    window_params.native_decorations = profile->GetPrefs()->GetBoolean(
        vivaldiprefs::kWindowsUseNativeDecoration);
  }

  window_params.minimum_size = gfx::Size(min_width, min_height);
  window_params.state = ui::SHOW_STATE_DEFAULT;
  window_params.resource_relative_url = std::move(params->url);
  window_params.creator_frame = render_frame_host();

  if (profile->IsGuestSession() && !incognito) {
    // Opening a new window from a guest session is only allowed for
    // incognito windows.  It will crash on purpose otherwise.
    // See Browser::Browser() for the CHECKs.
    return RespondNow(
        Error("New guest window can only be opened from incognito window"));
  }

  VivaldiBrowserWindow* window = new VivaldiBrowserWindow();
  // Delay sending the response until the newly created window has finished its
  // navigation or was closed during that process.
  window->SetDidFinishNavigationCallback(
      base::BindOnce(&WindowPrivateCreateFunction::OnAppUILoaded, this));

  Browser::Type window_type = Browser::TYPE_NORMAL;
  // Popup and settingswindow should open as popup and not stored in session.
  if (params->type != vivaldi::window_private::WindowType::WINDOW_TYPE_NORMAL) {
    window_type = Browser::TYPE_POPUP;
  }
  Browser::CreateParams create_params(window_type, profile, false);

  create_params.initial_bounds = window_bounds;

  create_params.creation_source = Browser::CreationSource::kStartupCreator;
  create_params.is_vivaldi = true;
  create_params.window = window;
  create_params.viv_ext_data = viv_ext_data;
  std::unique_ptr<Browser> browser(Browser::Create(create_params));
  DCHECK(browser->window() == window);
  window->SetWindowURL(window_params.resource_relative_url);
  window->CreateWebContents(std::move(browser), window_params);

  if (!tab_url.empty()) {
    content::OpenURLParams urlparams(GURL(tab_url), content::Referrer(),
                                     WindowOpenDisposition::NEW_FOREGROUND_TAB,
                                     ui::PAGE_TRANSITION_AUTO_TOPLEVEL, false);
    window->browser()->OpenURL(urlparams);
  }

  // TODO(pettern): If we ever need to open unfocused windows, we need to
  // add a new method for open delayed and unfocused.
  //  window->Show(focused ? AppWindow::SHOW_ACTIVE : AppWindow::SHOW_INACTIVE);

  return RespondLater();
}

void WindowPrivateCreateFunction::OnAppUILoaded(VivaldiBrowserWindow* window) {
  namespace Results = vivaldi::window_private::Create::Results;

  DCHECK(!did_respond());

  int window_id = window ? window->id() : -1;
  Respond(ArgumentList(Results::Create(window_id)));
}

ExtensionFunction::ResponseAction WindowPrivateGetCurrentIdFunction::Run() {
  namespace Results = vivaldi::window_private::GetCurrentId::Results;

  // It is OK to use GetSenderWebContents() as JS will call this function from
  // the proper window.
  content::WebContents* web_contents = GetSenderWebContents();
  Browser* browser = ::vivaldi::FindBrowserForEmbedderWebContents(web_contents);
  if (!browser)
    return RespondNow(Error("No Browser instance"));

  return RespondNow(ArgumentList(Results::Create(browser->session_id().id())));
}

ExtensionFunction::ResponseAction WindowPrivateSetStateFunction::Run() {
  using vivaldi::window_private::SetState::Params;

  std::unique_ptr<Params> params = Params::Create(args());
  EXTENSION_FUNCTION_VALIDATE(params.get());

  Browser* browser;
  std::string error;
  bool was_fullscreen;
  if (!windows_util::GetBrowserFromWindowID(
          this, params->window_id, WindowController::GetAllWindowFilter(),
          &browser, &error)) {
    return RespondNow(Error(error));
  }
  ui::WindowShowState show_state =
      vivaldi::ConvertToWindowShowState(params->state);

  // Don't trigger onStateChanged event for changes coming from JS. The
  // assumption is that JS updates its state as needed after each
  // windowPrivate.setState() call.
  static_cast<VivaldiBrowserWindow*>(browser->window())
      ->SetWindowState(show_state);

  switch (show_state) {
    case ui::SHOW_STATE_MINIMIZED:
      was_fullscreen = browser->window()->IsFullscreen();
      browser->extension_window_controller()->window()->Minimize();
      if (was_fullscreen) {
        browser->extension_window_controller()->SetFullscreenMode(
            false, extension()->url());
      }
      break;
    case ui::SHOW_STATE_MAXIMIZED:
      was_fullscreen = browser->window()->IsFullscreen();
#if BUILDFLAG(IS_MAC)
      // NOTE(bjorgvin@vivaldi.com): VB-82933 SetFullscreenMode has to be after
      // Maximize on macOS.
      browser->extension_window_controller()->window()->Maximize();
      if (was_fullscreen) {
        browser->extension_window_controller()->SetFullscreenMode(
            false, extension()->url());
      }
#else
      // NOTE(bjorgvin@vivaldi.com): VB-83626 SetFullscreenMode has to be before
      // Maximize on Linux to prevent triggering of extra onStateChanged events.
      if (was_fullscreen) {
        browser->extension_window_controller()->SetFullscreenMode(
            false, extension()->url());
      }
      browser->extension_window_controller()->window()->Maximize();
#endif
      break;
    case ui::SHOW_STATE_FULLSCREEN:
      browser->extension_window_controller()->SetFullscreenMode(
          true, extension()->url());
      break;
    case ui::SHOW_STATE_NORMAL:
      was_fullscreen = browser->window()->IsFullscreen();
      if (was_fullscreen) {
        browser->extension_window_controller()->SetFullscreenMode(
            false, extension()->url());
      } else {
        browser->window()->Restore();
      }
      break;
    default:
      break;
  }
  return RespondNow(NoArguments());
}

ExtensionFunction::ResponseAction
WindowPrivateUpdateMaximizeButtonPositionFunction::Run() {
  using vivaldi::window_private::UpdateMaximizeButtonPosition::Params;

  std::unique_ptr<Params> params = Params::Create(args());
  EXTENSION_FUNCTION_VALIDATE(params.get());

  Browser* browser;
  std::string error;
  if (!windows_util::GetBrowserFromWindowID(
          this, params->window_id, WindowController::GetAllWindowFilter(),
          &browser, &error)) {
    return RespondNow(Error(error));
  }
  VivaldiBrowserWindow* window = VivaldiBrowserWindow::FromBrowser(browser);
  if (window) {
    gfx::RectF rect(params->left, params->top, params->width, params->height);
    ::vivaldi::FromUICoordinates(window->web_contents(), &rect);
    gfx::Rect int_rect(std::round(rect.x()), std::round(rect.y()),
                       std::round(rect.width()), std::round(rect.height()));
    window->UpdateMaximizeButtonPosition(int_rect);
  }
  return RespondNow(NoArguments());
}

ExtensionFunction::ResponseAction
WindowPrivateGetFocusedElementInfoFunction::Run() {
  using vivaldi::window_private::GetFocusedElementInfo::Params;

  std::unique_ptr<Params> params = Params::Create(args());
  EXTENSION_FUNCTION_VALIDATE(params);

  VivaldiBrowserWindow* window =
      VivaldiBrowserWindow::FromId(params->window_id);
  if (!window) {
    return RespondNow(Error("No such window"));
  }

  content::WebContentsImpl* web_contents =
      static_cast<content::WebContentsImpl*>(window->web_contents());
  content::RenderFrameHostImpl* render_frame_host =
      web_contents->GetFocusedFrame();
  if (!render_frame_host) {
    render_frame_host = web_contents->GetPrimaryMainFrame();
  }
  render_frame_host->GetVivaldiFrameService()->GetFocusedElementInfo(
      mojo::WrapCallbackWithDefaultInvokeIfNotRun(
          base::BindOnce(&WindowPrivateGetFocusedElementInfoFunction::
                             FocusedElementInfoReceived,
                         this),
          "", "", false, ""));

  return RespondLater();
}

void WindowPrivateGetFocusedElementInfoFunction::FocusedElementInfoReceived(
    const std::string& tag_name,
    const std::string& type,
    bool editable,
    const std::string& role) {
  namespace Results = vivaldi::window_private::GetFocusedElementInfo::Results;

  vivaldi::window_private::FocusedElementInfo info;
  info.tag_name = tag_name;
  info.type = type;
  info.editable = editable;
  info.role = role;
  return Respond(ArgumentList(Results::Create(info)));
}

}  // namespace extensions
