// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "android_webview/common/aw_features.h"
#include "base/feature_list.h"
#include "base/metrics/field_trial_params.h"

namespace android_webview {
namespace features {

// Alphabetical:

// Enable brotli compression support in WebView.
BASE_FEATURE(kWebViewBrotliSupport,
             "WebViewBrotliSupport",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Check layer_tree_frame_sink_id when return resources to compositor.
BASE_FEATURE(kWebViewCheckReturnResources,
             "WebViewCheckReturnResources",
             base::FEATURE_ENABLED_BY_DEFAULT);

// Use the SafeBrowsingApiHandlerBridge which uses the connectionless GMS APIs.
// This Feature is checked and used in downstream internal code.
BASE_FEATURE(kWebViewConnectionlessSafeBrowsing,
             "WebViewConnectionlessSafeBrowsing",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Enable WebView to automatically darken the page in FORCE_DARK_AUTO mode if
// the app's theme is dark.
BASE_FEATURE(kWebViewForceDarkModeMatchTheme,
             "WebViewForceDarkModeMatchTheme",
             base::FEATURE_DISABLED_BY_DEFAULT);

BASE_FEATURE(kWebViewHitTestInBlinkOnTouchStart,
             "WebViewHitTestInBlinkOnTouchStart",
             base::FEATURE_ENABLED_BY_DEFAULT);

// Enable display cutout support for Android P and above.
BASE_FEATURE(kWebViewDisplayCutout,
             "WebViewDisplayCutout",
             base::FEATURE_ENABLED_BY_DEFAULT);

// Fake empty component to measure component updater performance impact on
// WebView clients.
BASE_FEATURE(kWebViewEmptyComponentLoaderPolicy,
             "WebViewEmptyComponentLoaderPolicy",
             base::FEATURE_DISABLED_BY_DEFAULT);

// When enabled, passive mixed content (Audio/Video/Image subresources loaded
// over HTTP on HTTPS sites) will be autoupgraded to HTTPS, and the load will be
// blocked if the resource fails to load over HTTPS. This only affects apps that
// set the mixed content mode to MIXED_CONTENT_COMPATIBILITY_MODE, autoupgrades
// are always disabled for MIXED_CONTENT_NEVER_ALLOW and
// MIXED_CONTENT_ALWAYS_ALLOW modes.
BASE_FEATURE(kWebViewMixedContentAutoupgrades,
             "WebViewMixedContentAutoupgrades",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Only allow extra headers added via loadUrl() to be sent to the original
// origin; strip them from the request if a cross-origin redirect occurs.
BASE_FEATURE(kWebViewExtraHeadersSameOriginOnly,
             "WebViewExtraHeadersSameOriginOnly",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Enable the new Java/JS Bridge code path with mojo implementation.
BASE_FEATURE(kWebViewJavaJsBridgeMojo,
             "WebViewJavaJsBridgeMojo",
             base::FEATURE_DISABLED_BY_DEFAULT);

// When enabled, connections using legacy TLS 1.0/1.1 versions are allowed.
BASE_FEATURE(kWebViewLegacyTlsSupport,
             "WebViewLegacyTlsSupport",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Measure the number of pixels occupied by one or more WebViews as a
// proportion of the total screen size. Depending on the number of
// WebVieaws and the size of the screen this might be expensive so
// hidden behind a feature flag until the true runtime cost can be
// measured.
BASE_FEATURE(kWebViewMeasureScreenCoverage,
             "WebViewMeasureScreenCoverage",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Field trial feature for controlling support of Origin Trials on WebView.
BASE_FEATURE(kWebViewOriginTrials,
             "WebViewOriginTrials",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Whether to record size of the embedding app's data directory to the UMA
// histogram Android.WebView.AppDataDirectorySize.
BASE_FEATURE(kWebViewRecordAppDataDirectorySize,
             "WebViewRecordAppDataDirectorySize",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Disallows window.{alert, prompt, confirm} if triggered inside a subframe that
// is not same origin with the main frame.
BASE_FEATURE(kWebViewSuppressDifferentOriginSubframeJSDialogs,
             "WebViewSuppressDifferentOriginSubframeJSDialogs",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Only synthesize page load for URL spoof prevention at most once, on initial
// main document access (instead on every NavigationStateChanged call that
// invalidates the URL after).
BASE_FEATURE(kWebViewSynthesizePageLoadOnlyOnInitialMainDocumentAccess,
             "WebViewSynthesizePageLoadOnlyOnInitialMainDocumentAccess",
             base::FEATURE_ENABLED_BY_DEFAULT);

// A Feature used for WebView variations tests. Not used in production.
BASE_FEATURE(kWebViewTestFeature,
             "WebViewTestFeature",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Use WebView's nonembedded MetricsUploadService to upload UMA metrics instead
// of sending it directly to GMS-core.
BASE_FEATURE(kWebViewUseMetricsUploadService,
             "WebViewUseMetricsUploadService",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Enable raster in wide color gamut for apps that use webview in a wide color
// gamut activity.
BASE_FEATURE(kWebViewWideColorGamutSupport,
             "WebViewWideColorGamutSupport",
             base::FEATURE_ENABLED_BY_DEFAULT);

// Control the default behaviour for the XRequestedWith header
BASE_FEATURE(kWebViewXRequestedWithHeaderControl,
             "WebViewXRequestedWithHeaderControl",
             base::FEATURE_DISABLED_BY_DEFAULT);

// Default value of the XRequestedWith header mode when
// WebViewXRequestedWithHeaderControl is enabled. Defaults to
// |AwSettings::RequestedWithHeaderMode::NO_HEADER| Must be value declared in in
// |AwSettings::RequestedWithHeaderMode|
const base::FeatureParam<int> kWebViewXRequestedWithHeaderMode{
    &kWebViewXRequestedWithHeaderControl, "WebViewXRequestedWithHeaderMode", 0};

// Control whether WebView will attempt to read the XRW header allow-list from
// the manifest.
BASE_FEATURE(kWebViewXRequestedWithHeaderManifestAllowList,
             "WebViewXRequestedWithHeaderManifestAllowList",
             base::FEATURE_DISABLED_BY_DEFAULT);

// This persists client hints between top-level navigations.
BASE_FEATURE(kWebViewClientHintsControllerDelegate,
             "WebViewClientHintsControllerDelegate",
             base::FEATURE_DISABLED_BY_DEFAULT);

}  // namespace features
}  // namespace android_webview
