// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_WEBVIEW_BROWSER_NETWORK_SERVICE_AW_PROXYING_RESTRICTED_COOKIE_MANAGER_H_
#define ANDROID_WEBVIEW_BROWSER_NETWORK_SERVICE_AW_PROXYING_RESTRICTED_COOKIE_MANAGER_H_

#include <string>

#include "base/memory/weak_ptr.h"
#include "mojo/public/cpp/bindings/remote.h"
#include "services/network/public/mojom/restricted_cookie_manager.mojom.h"

class GURL;

namespace android_webview {

// A RestrictedCookieManager which conditionally proxies to an underlying
// RestrictedCookieManager, first consulting WebView's cookie settings.
class AwProxyingRestrictedCookieManager
    : public network::mojom::RestrictedCookieManager {
 public:
  // Creates a AwProxyingRestrictedCookieManager that lives on IO thread,
  // binding it to handle communications from |receiver|. The requests will be
  // delegated to |underlying_rcm|. The resulting object will be owned by the
  // pipe corresponding to |request| and will in turn own |underlying_rcm|.
  //
  // Expects to be called on the UI thread.
  static void CreateAndBind(
      mojo::PendingRemote<network::mojom::RestrictedCookieManager>
          underlying_rcm,
      bool is_service_worker,
      int process_id,
      int frame_id,
      mojo::PendingReceiver<network::mojom::RestrictedCookieManager> receiver);

  AwProxyingRestrictedCookieManager(const AwProxyingRestrictedCookieManager&) =
      delete;
  AwProxyingRestrictedCookieManager& operator=(
      const AwProxyingRestrictedCookieManager&) = delete;

  ~AwProxyingRestrictedCookieManager() override;

  // network::mojom::RestrictedCookieManager interface:
  void GetAllForUrl(const GURL& url,
                    const net::SiteForCookies& site_for_cookies,
                    const url::Origin& top_frame_origin,
                    network::mojom::CookieManagerGetOptionsPtr options,
                    bool partitioned_cookies_runtime_feature_enabled,
                    GetAllForUrlCallback callback) override;
  void SetCanonicalCookie(const net::CanonicalCookie& cookie,
                          const GURL& url,
                          const net::SiteForCookies& site_for_cookies,
                          const url::Origin& top_frame_origin,
                          net::CookieInclusionStatus status,
                          SetCanonicalCookieCallback callback) override;
  void AddChangeListener(
      const GURL& url,
      const net::SiteForCookies& site_for_cookies,
      const url::Origin& top_frame_origin,
      mojo::PendingRemote<network::mojom::CookieChangeListener> listener,
      AddChangeListenerCallback callback) override;

  void SetCookieFromString(const GURL& url,
                           const net::SiteForCookies& site_for_cookies,
                           const url::Origin& top_frame_origin,
                           const std::string& cookie,
                           bool partitioned_cookies_runtime_feature_enabled,
                           SetCookieFromStringCallback callback) override;

  void GetCookiesString(const GURL& url,
                        const net::SiteForCookies& site_for_cookies,
                        const url::Origin& top_frame_origin,
                        bool partitioned_cookies_runtime_feature_enabled,
                        GetCookiesStringCallback callback) override;

  void CookiesEnabledFor(const GURL& url,
                         const net::SiteForCookies& site_for_cookies,
                         const url::Origin& top_frame_origin,
                         CookiesEnabledForCallback callback) override;

  // This one is internal.
  bool AllowCookies(const GURL& url,
                    const net::SiteForCookies& site_for_cookies) const;

  // TODO(https://crbug.com/1296161): Delete this function.
  void ConvertPartitionedCookiesToUnpartitioned(const GURL& url) override;

 private:
  AwProxyingRestrictedCookieManager(
      mojo::PendingRemote<network::mojom::RestrictedCookieManager>
          underlying_restricted_cookie_manager,
      bool is_service_worker,
      int process_id,
      int frame_id);

  static void CreateAndBindOnIoThread(
      mojo::PendingRemote<network::mojom::RestrictedCookieManager>
          underlying_rcm,
      bool is_service_worker,
      int process_id,
      int frame_id,
      mojo::PendingReceiver<network::mojom::RestrictedCookieManager> receiver);

  mojo::Remote<network::mojom::RestrictedCookieManager>
      underlying_restricted_cookie_manager_;
  bool is_service_worker_;
  int process_id_;
  int frame_id_;

  base::WeakPtrFactory<AwProxyingRestrictedCookieManager> weak_factory_{this};
};

}  // namespace android_webview

#endif  // ANDROID_WEBVIEW_BROWSER_NETWORK_SERVICE_AW_PROXYING_RESTRICTED_COOKIE_MANAGER_H_
