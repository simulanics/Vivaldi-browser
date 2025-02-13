// Copyright 2012 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CONTENT_BROWSER_BROWSER_PLUGIN_BROWSER_PLUGIN_GUEST_H_
#define CONTENT_BROWSER_BROWSER_PLUGIN_BROWSER_PLUGIN_GUEST_H_

#include <vector>

#include "base/memory/raw_ptr.h"
#include "build/build_config.h"
#include "content/public/browser/browser_plugin_guest_delegate.h"
#include "content/public/browser/web_contents_observer.h"
#include "mojo/public/cpp/bindings/pending_remote.h"
#include "third_party/blink/public/mojom/choosers/popup_menu.mojom.h"

namespace content {
class WebContentsImpl;

// A browser plugin guest provides functionality for WebContents to operate in
// the guest role.
//
// When a guest is initially created, it is in an unattached state. That is,
// it is not visible anywhere and has no embedder WebContents assigned.
// A BrowserPluginGuest is said to be "attached" if it has an embedder.
// A BrowserPluginGuest can also create a new unattached guest via
// CreateNewWindow. The newly created guest will live in the same partition,
// which means it can share storage and can script this guest.
//
// TODO(wjmaclean): Get rid of "BrowserPlugin" in the name of this class.
// Perhaps "InnerWebContentsGuestConnector"?
class BrowserPluginGuest : public WebContentsObserver {
 public:
  BrowserPluginGuest(const BrowserPluginGuest&) = delete;
  BrowserPluginGuest& operator=(const BrowserPluginGuest&) = delete;

  ~BrowserPluginGuest() override;

  // The WebContents passed into the factory method here has not been
  // initialized yet and so it does not yet hold a SiteInstance.
  // BrowserPluginGuest must be constructed and installed into a WebContents
  // prior to its initialization because WebContents needs to determine what
  // type of WebContentsView to construct on initialization. The content
  // embedder needs to be aware of |guest_site_instance| on the guest's
  // construction and so we pass it in here.
  //
  // After this, a new BrowserPluginGuest is created with ownership transferred
  // into the |web_contents|.
  static void CreateInWebContents(WebContentsImpl* web_contents,
                                  BrowserPluginGuestDelegate* delegate);

  // BrowserPluginGuest::Init is called after the associated guest WebContents
  // initializes. This sets up the appropriate blink::RendererPreferences so
  // that this guest can navigate and resize offscreen.
  void Init();

  // Creates a new guest WebContentsImpl with the provided |params| with |this|
  // as the |opener|.
  std::unique_ptr<WebContentsImpl> CreateNewGuestWindow(
      const WebContents::CreateParams& params);

  // WebContentsObserver implementation.
  void DidStartNavigation(NavigationHandle* navigation_handle) override;
  void DidFinishNavigation(NavigationHandle* navigation_handle) override;

  void PrimaryMainFrameRenderProcessGone(
      base::TerminationStatus status) override;
#if BUILDFLAG(IS_MAC)
  // On MacOS X popups are painted by the browser process. We handle them here
  // so that they are positioned correctly.
  void ShowPopupMenu(
      RenderFrameHost* render_frame_host,
      mojo::PendingRemote<blink::mojom::PopupMenuClient>* popup_client,
      const gfx::Rect& bounds,
      int32_t item_height,
      double font_size,
      int32_t selected_item,
      std::vector<blink::mojom::MenuItemPtr>* menu_items,
      bool right_aligned,
      bool allow_multiple_selection);
#endif

  WebContentsImpl* GetWebContents() const;

  // We need to change the delegate when we use the content from the
  // tab-strip.
  void set_delegate(BrowserPluginGuestDelegate* delegate) {
    delegate_ = delegate;
  }


 private:
  // BrowserPluginGuest is a WebContentsObserver of |web_contents| and
  // |web_contents| has to stay valid for the lifetime of BrowserPluginGuest.
  BrowserPluginGuest(WebContentsImpl* web_contents,
                     BrowserPluginGuestDelegate* delegate);

  void InitInternal(WebContentsImpl* owner_web_contents);

  raw_ptr<BrowserPluginGuestDelegate> delegate_;
};

}  // namespace content

#endif  // CONTENT_BROWSER_BROWSER_PLUGIN_BROWSER_PLUGIN_GUEST_H_
