// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_CHROME_BROWSER_SAFE_BROWSING_TAILORED_SECURITY_TAILORED_SECURITY_TAB_HELPER_H_
#define IOS_CHROME_BROWSER_SAFE_BROWSING_TAILORED_SECURITY_TAILORED_SECURITY_TAB_HELPER_H_

#import "components/safe_browsing/core/browser/tailored_security_service/tailored_security_service_observer.h"
#import "ios/web/public/web_state_observer.h"
#import "ios/web/public/web_state_user_data.h"

namespace safe_browsing {
class TailoredSecurityService;
}

// A tab helper that uses Tailored Security Service for promoting users to
// enable better levels of Safe Browsing.
class TailoredSecurityTabHelper
    : public safe_browsing::TailoredSecurityServiceObserver,
      public web::WebStateObserver,
      public web::WebStateUserData<TailoredSecurityTabHelper> {
 public:
  TailoredSecurityTabHelper(web::WebState* web_state,
                            safe_browsing::TailoredSecurityService* service);
  ~TailoredSecurityTabHelper() override;

  TailoredSecurityTabHelper(const TailoredSecurityTabHelper&) = delete;
  TailoredSecurityTabHelper& operator=(TailoredSecurityTabHelper&) = delete;

  // web::WebStateObserver
  void DidFinishNavigation(web::WebState* web_state,
                           web::NavigationContext* navigation_context) override;
  void WasShown(web::WebState* web_state) override;
  void WasHidden(web::WebState* web_state) override;
  void WebStateDestroyed(web::WebState* web_state) override;

  // safe_browsing::TailoredSecurityServiceObserver.
  void OnTailoredSecurityBitChanged(bool enabled,
                                    base::Time previous_update) override;
  void OnTailoredSecurityServiceDestroyed() override;
  void OnSyncNotificationMessageRequest(bool is_enabled) override;

 private:
  friend class web::WebStateUserData<TailoredSecurityTabHelper>;

  void UpdateFocusAndURL(bool focused, const GURL& url);

  WEB_STATE_USER_DATA_KEY_DECL();

  // Reference to the TailoredSecurityService for the BrowserState.
  safe_browsing::TailoredSecurityService* service_;

  // Whether the WebState is currently in focus.
  bool focused_ = false;

  // The most recent URL the WebState navigated to.
  GURL last_url_;

  // Whether we currently have a query request.
  bool has_query_request_ = false;

  // Associated WebState.
  web::WebState* web_state_;
};

#endif  // IOS_CHROME_BROWSER_SAFE_BROWSING_TAILORED_SECURITY_TAILORED_SECURITY_TAB_HELPER_H_
