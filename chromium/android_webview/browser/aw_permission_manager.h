// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_WEBVIEW_BROWSER_AW_PERMISSION_MANAGER_H_
#define ANDROID_WEBVIEW_BROWSER_AW_PERMISSION_MANAGER_H_

#include <memory>

#include "base/callback_forward.h"
#include "base/containers/id_map.h"
#include "base/memory/weak_ptr.h"
#include "content/public/browser/permission_controller_delegate.h"
#include "content/public/browser/permission_result.h"

namespace blink {
enum class PermissionType;
}

namespace android_webview {

class AwBrowserPermissionRequestDelegate;
class LastRequestResultCache;

class AwPermissionManager : public content::PermissionControllerDelegate {
 public:
  AwPermissionManager();

  AwPermissionManager(const AwPermissionManager&) = delete;
  AwPermissionManager& operator=(const AwPermissionManager&) = delete;

  ~AwPermissionManager() override;

  // PermissionControllerDelegate implementation.
  void RequestPermission(
      blink::PermissionType permission,
      content::RenderFrameHost* render_frame_host,
      const GURL& requesting_origin,
      bool user_gesture,
      base::OnceCallback<void(blink::mojom::PermissionStatus)> callback)
      override;
  void RequestPermissions(
      const std::vector<blink::PermissionType>& permissions,
      content::RenderFrameHost* render_frame_host,
      const GURL& requesting_origin,
      bool user_gesture,
      base::OnceCallback<
          void(const std::vector<blink::mojom::PermissionStatus>&)> callback)
      override;
  void ResetPermission(blink::PermissionType permission,
                       const GURL& requesting_origin,
                       const GURL& embedding_origin) override;
  void RequestPermissionsFromCurrentDocument(
      const std::vector<blink::PermissionType>& permissions,
      content::RenderFrameHost* render_frame_host,
      bool user_gesture,
      base::OnceCallback<
          void(const std::vector<blink::mojom::PermissionStatus>&)> callback)
      override;
  blink::mojom::PermissionStatus GetPermissionStatus(
      blink::PermissionType permission,
      const GURL& requesting_origin,
      const GURL& embedding_origin) override;
  content::PermissionResult GetPermissionResultForOriginWithoutContext(
      blink::PermissionType permission,
      const url::Origin& origin) override;
  blink::mojom::PermissionStatus GetPermissionStatusForCurrentDocument(
      blink::PermissionType permission,
      content::RenderFrameHost* render_frame_host) override;
  blink::mojom::PermissionStatus GetPermissionStatusForWorker(
      blink::PermissionType permission,
      content::RenderProcessHost* render_process_host,
      const GURL& worker_origin) override;
  SubscriptionId SubscribePermissionStatusChange(
      blink::PermissionType permission,
      content::RenderProcessHost* render_process_host,
      content::RenderFrameHost* render_frame_host,
      const GURL& requesting_origin,
      base::RepeatingCallback<void(blink::mojom::PermissionStatus)> callback)
      override;
  void UnsubscribePermissionStatusChange(
      SubscriptionId subscription_id) override;

 protected:
  void CancelPermissionRequest(int request_id);
  void CancelPermissionRequests();

 private:
  class PendingRequest;
  using PendingRequestsMap = base::IDMap<std::unique_ptr<PendingRequest>>;

  virtual int GetRenderProcessID(content::RenderFrameHost* render_frame_host);
  virtual int GetRenderFrameID(content::RenderFrameHost* render_frame_host);
  virtual GURL LastCommittedOrigin(content::RenderFrameHost* render_frame_host);
  virtual AwBrowserPermissionRequestDelegate* GetDelegate(int render_process_id,
                                                          int render_frame_id);

  // The weak pointer to this is used to clean up any information which is
  // stored in the pending request or result cache maps. However, the callback
  // should be run regardless of whether the class is still alive so the method
  // is static.
  static void OnRequestResponse(
      const base::WeakPtr<AwPermissionManager>& manager,
      int request_id,
      blink::PermissionType permission,
      bool allowed);

  PendingRequestsMap pending_requests_;
  std::unique_ptr<LastRequestResultCache> result_cache_;

  base::WeakPtrFactory<AwPermissionManager> weak_ptr_factory_{this};
};

} // namespace android_webview

#endif // ANDROID_WEBVIEW_BROWSER_AW_PERMISSION_MANAGER_H_
