// Copyright 2017 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/activity_services/canonical_url_retriever.h"

#import "base/bind.h"
#import "base/metrics/histogram_macros.h"
#import "base/strings/utf_string_conversions.h"
#import "base/values.h"
#import "components/ui_metrics/canonical_url_share_metrics_types.h"
#import "ios/chrome/browser/procedural_block_types.h"
#import "ios/web/public/js_messaging/web_frame.h"
#import "ios/web/public/js_messaging/web_frame_util.h"
#import "ios/web/public/web_state.h"
#import "url/gurl.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {

// Logs `result` in the Mobile.CanonicalURLResult histogram.
void LogCanonicalUrlResultHistogram(ui_metrics::CanonicalURLResult result) {
  UMA_HISTOGRAM_ENUMERATION(ui_metrics::kCanonicalURLResultHistogram, result,
                            ui_metrics::CANONICAL_URL_RESULT_COUNT);
}

// Converts a `value` to a GURL. Returns an empty GURL if `value` is not a valid
// HTTPS URL, indicating that retrieval failed. This function also handles
// logging retrieval failures if applicable.
GURL UrlFromValue(const base::Value* value) {
  GURL canonical_url;
  bool canonical_url_found = false;

  if (value && value->is_string() && !value->GetString().empty()) {
    canonical_url = GURL(value->GetString());

    // This variable is required for metrics collection in order to distinguish
    // between the no canonical URL found and the invalid canonical URL found
    // cases. The `canonical_url` GURL cannot be relied upon to distinguish
    // between these cases because GURLs created with invalid URLs can be
    // constructed as empty GURLs.
    canonical_url_found = true;
  }

  if (!canonical_url_found) {
    // Log result if no canonical URL is found.
    LogCanonicalUrlResultHistogram(ui_metrics::FAILED_NO_CANONICAL_URL_DEFINED);
  } else if (!canonical_url.is_valid()) {
    // Log result if an invalid canonical URL is found.
    LogCanonicalUrlResultHistogram(ui_metrics::FAILED_CANONICAL_URL_INVALID);
  }

  return canonical_url.is_valid() ? canonical_url : GURL::EmptyGURL();
}
}  // namespace

namespace activity_services {

const char16_t kCanonicalURLScript[] =
    u"(function() {"
    u"  var linkNode = document.querySelector(\"link[rel='canonical']\");"
    u"  return linkNode ? linkNode.getAttribute(\"href\") : \"\";"
    u"})()";

void RetrieveCanonicalUrl(web::WebState* web_state,
                          ProceduralBlockWithURL completion) {
  // Do not use the canonical URL if the page is not secured with HTTPS.
  const GURL visible_url = web_state->GetVisibleURL();
  if (!visible_url.SchemeIsCryptographic()) {
    LogCanonicalUrlResultHistogram(ui_metrics::FAILED_VISIBLE_URL_NOT_HTTPS);
    completion(GURL::EmptyGURL());
    return;
  }

  web::WebFrame* main_frame = web::GetMainFrame(web_state);
  if (!main_frame) {
    completion(GURL::EmptyGURL());
    return;
  }

  void (^javascript_completion)(const base::Value*) =
      ^(const base::Value* value) {
        GURL canonical_url = UrlFromValue(value);

        // If the canonical URL is not empty, then the retrieval was successful,
        // and the success can be logged.
        if (!canonical_url.is_empty()) {
          // Log whether the success occurred with an HTTP canonical URL, a
          // canonical URL that is the same as the visible URL, or a canonical
          // URL that is different from the visible URL.
          LogCanonicalUrlResultHistogram(
              !canonical_url.SchemeIsCryptographic()
                  ? ui_metrics::SUCCESS_CANONICAL_URL_NOT_HTTPS
                  : visible_url == canonical_url
                        ? ui_metrics::SUCCESS_CANONICAL_URL_SAME_AS_VISIBLE
                        : ui_metrics::
                              SUCCESS_CANONICAL_URL_DIFFERENT_FROM_VISIBLE);
        }

        completion(canonical_url);
      };

  main_frame->ExecuteJavaScript(kCanonicalURLScript,
                                base::BindOnce(javascript_completion));
}
}  // namespace activity_services
