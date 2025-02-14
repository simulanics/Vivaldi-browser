// Copyright 2017 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_WEB_COMMON_FEATURES_H_
#define IOS_WEB_COMMON_FEATURES_H_

#include "base/feature_list.h"

namespace web {
namespace features {

// Used to crash the browser if unexpected URL change is detected.
// https://crbug.com/841105.
BASE_DECLARE_FEATURE(kCrashOnUnexpectedURLChange);

// Used to prevent native apps from being opened when a universal link is tapped
// and the user is browsing in off the record mode.
BASE_DECLARE_FEATURE(kBlockUniversalLinksInOffTheRecordMode);

// Used to ensure that the render is not suspended.
BASE_DECLARE_FEATURE(kKeepsRenderProcessAlive);

// Used to enable the workaround for a WKWebView WKNavigation leak.
// (crbug.com/1010765).  Clear older pending navigation records when a
// navigation finishes.
BASE_DECLARE_FEATURE(kClearOldNavigationRecordsWorkaround);

// Feature flag enabling persistent downloads.
BASE_DECLARE_FEATURE(kEnablePersistentDownloads);

// When enabled, preserves properties of the UIScrollView using CRWPropertyStore
// when the scroll view is recreated. When disabled, only preserve a small set
// of properties using hard coded logic.
BASE_DECLARE_FEATURE(kPreserveScrollViewProperties);

// Records snapshot size of image (IOS.Snapshots.ImageSize histogram) and PDF
// (IOS.Snapshots.PDFSize histogram) if enabled. Enabling this flag will
// generate PDF when Page Snapshot is taken just to record PDF size.
BASE_DECLARE_FEATURE(kRecordSnapshotSize);

// When enabled, the `attribution` property of NSMutableURLRequests passed to
// WKWebView is set as NSURLRequestAttributionUser on iOS 15.
BASE_DECLARE_FEATURE(kSetRequestAttribution);

// Feature flag that enable Shared Highlighting color change in iOS.
BASE_DECLARE_FEATURE(kIOSSharedHighlightingColorChange);

// Feature flag that enables native session restoration with a synthesized
// interaction state.
BASE_DECLARE_FEATURE(kSynthesizedRestoreSession);

// Enables user control for camera and/or microphone access for a specific site
// through site settings during its lifespan. When enabled, each web state will
// keep track of whether camera and/or microphone access is granted by the user
// for its current site.
BASE_DECLARE_FEATURE(kMediaPermissionsControl);

// Enables the Fullscreen API in WebKit (supported on iOS 16.0+). This API
// allows web sites to enter fullscreen mode, with all browser UI hidden.
BASE_DECLARE_FEATURE(kEnableFullscreenAPI);

// Feature flag enabling use of new iOS 15
// loadSimulatedRequest:responseHTMLString: API to display error pages in
// CRWWKNavigationHandler. The helper method IsLoadSimulatedRequestAPIEnabled()
// should be used instead of directly checking this feature.
BASE_DECLARE_FEATURE(kUseLoadSimulatedRequestForOfflinePage);

// Feature flag that enable web page detected intents annotations.
BASE_DECLARE_FEATURE(kEnableWebPageAnnotations);

// Feature flag that enables getting more of the surrounding text when the user
// long presses at a certain location.
BASE_DECLARE_FEATURE(kLongPressSurroundingText);

// When enabled, CRWWebViewScrollViewProxy's `scrollEnabled` state is not
// restored if the new instance already has the same `scrollEnabled` state as
// the old one.
BASE_DECLARE_FEATURE(kScrollViewProxyScrollEnabledWorkaround);

// When true, user control for camera and/or microphone access should be
// enabled.
bool IsMediaPermissionsControlEnabled();

// When true, the new loadSimulatedRequest API should be used when displaying
// error pages.
bool IsLoadSimulatedRequestAPIEnabled();

}  // namespace features
}  // namespace web

#endif  // IOS_WEB_COMMON_FEATURES_H_
