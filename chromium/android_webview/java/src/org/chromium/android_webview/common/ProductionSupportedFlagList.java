// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview.common;

import org.chromium.base.BaseFeatures;
import org.chromium.base.BaseSwitches;
import org.chromium.blink_public.common.BlinkFeatures;
import org.chromium.blink_scheduler.BlinkSchedulerFeatures;
import org.chromium.cc.base.CcFeatures;
import org.chromium.cc.base.CcSwitches;
import org.chromium.components.autofill.AutofillFeatures;
import org.chromium.components.feature_engagement.FeatureConstants;
import org.chromium.components.metrics.MetricsFeatures;
import org.chromium.components.metrics.MetricsSwitches;
import org.chromium.components.network_session_configurator.NetworkSessionSwitches;
import org.chromium.components.variations.VariationsSwitches;
import org.chromium.components.viz.common.VizFeatures;
import org.chromium.content_public.common.ContentFeatures;
import org.chromium.content_public.common.ContentSwitches;
import org.chromium.gpu.config.GpuFeatures;
import org.chromium.gpu.config.GpuSwitches;
import org.chromium.net.NetFeatures;
import org.chromium.services.network.NetworkServiceFeatures;
import org.chromium.ui.accessibility.AccessibilityFeatures;

/**
 * List of experimental features/flags supported for user devices. Add features/flags to this list
 * with scrutiny: any feature/flag in this list can be enabled for production Android devices, and
 * so it must not compromise the Android security model (i.e., WebView must still protect the app's
 * private data from being user visible).
 *
 * <p>
 * This lives in the common package so it can be shared by dev UI (to know which features/flags to
 * display) as well as the WebView implementation (so it knows which features/flags are safe to
 * honor).
 */
public final class ProductionSupportedFlagList {
    // Do not instantiate this class.
    private ProductionSupportedFlagList() {}

    /**
     * A list of commandline flags supported on user devices. If updating this list, please also
     * update enums.xml. See android_webview/docs/developer-ui.md
     * (https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/developer-ui.md#Adding-your-flags-and-features-to-the-UI).
     */
    public static final Flag[] sFlagList = {
            Flag.commandLine(AwSwitches.HIGHLIGHT_ALL_WEBVIEWS,
                    "Highlight the contents (including web contents) of all WebViews with a yellow "
                            + "tint. This is useful for identifying WebViews in an Android "
                            + "application."),
            Flag.commandLine(AwSwitches.WEBVIEW_VERBOSE_LOGGING,
                    "WebView will log additional debugging information to logcat, such as "
                            + "variations and commandline state."),
            Flag.commandLine(CcSwitches.SHOW_COMPOSITED_LAYER_BORDERS,
                    "Renders a border around compositor layers to help debug and study layer "
                            + "compositing."),
            Flag.commandLine(CcSwitches.ANIMATED_IMAGE_RESUME,
                    "Resumes animated images from where they were."),
            Flag.commandLine(AwSwitches.FINCH_SEED_EXPIRATION_AGE,
                    "Forces all variations seeds to be considered stale.", "0"),
            Flag.commandLine(AwSwitches.FINCH_SEED_IGNORE_PENDING_DOWNLOAD,
                    "Forces the WebView service to reschedule a variations seed download job even "
                            + "if one is already pending."),
            Flag.commandLine(AwSwitches.FINCH_SEED_NO_CHARGING_REQUIREMENT,
                    "Forces WebView's service to always schedule a new variations seed download "
                            + "job, even if the device is not charging. Note this switch may be "
                            + "necessary for testing on Android emulators as these are not always "
                            + "considered to be charging."),
            Flag.commandLine(AwSwitches.FINCH_SEED_MIN_DOWNLOAD_PERIOD,
                    "Disables throttling of variations seed download jobs.", "0"),
            Flag.commandLine(AwSwitches.FINCH_SEED_MIN_UPDATE_PERIOD,
                    "Disables throttling of new variations seed requests to the WebView service.",
                    "0"),
            Flag.commandLine(MetricsSwitches.FORCE_ENABLE_METRICS_REPORTING,
                    "Forces WebView's metrics reporting to be enabled. This overrides user "
                            + "settings and capacity sampling, but does not override the app's "
                            + "choice to opt-out."),
            Flag.commandLine(AwSwitches.WEBVIEW_LOG_JS_CONSOLE_MESSAGES,
                    "Mirrors JavaScript console messages to system logs."),
            Flag.commandLine(BaseSwitches.ENABLE_CRASH_REPORTER_FOR_TESTING,
                    "Used for turning on Breakpad crash reporting in a debug environment where "
                            + "crash reporting is typically compiled but disabled."),
            Flag.commandLine(GpuSwitches.DISABLE_GPU_RASTERIZATION,
                    "Disables GPU rasterization, i.e. rasterizes on the CPU only."),
            Flag.commandLine(GpuSwitches.IGNORE_GPU_BLOCKLIST,
                    "Overrides the built-in software rendering list and enables "
                            + "GPU acceleration on unsupported device configurations."),
            Flag.commandLine(AwSwitches.WEBVIEW_ENABLE_MODERN_COOKIE_SAME_SITE,
                    "Enables modern SameSite cookie behavior: 1) SameSite=Lax by default "
                            + "(cookies without a SameSite attribute are treated as SameSite=Lax); "
                            + "2) Schemeful Same-Site (site boundaries include the URL scheme)."),
            Flag.commandLine(ContentSwitches.SITE_PER_PROCESS,
                    "Security mode that enables site isolation for all sites inside WebView. In "
                            + "this mode, each renderer process will contain pages from at most "
                            + "one site, using out-of-process iframes when needed. Highly "
                            + "experimental."),
            Flag.commandLine(NetworkSessionSwitches.ENABLE_HTTP2_GREASE_SETTINGS,
                    "Enable sending HTTP/2 SETTINGS parameters with reserved identifiers."),
            Flag.commandLine(NetworkSessionSwitches.DISABLE_HTTP2_GREASE_SETTINGS,
                    "Disable sending HTTP/2 SETTINGS parameters with reserved identifiers."),
            Flag.commandLine(VariationsSwitches.ENABLE_FINCH_SEED_DELTA_COMPRESSION,
                    "Enables delta-compression when requesting a new seed from the server."),
            Flag.commandLine(AwSwitches.WEBVIEW_MP_ARCH_FENCED_FRAMES,
                    "Enables MPArch-based fenced frames. Also implies SharedStorageAPI, "
                            + "and PrivacySandboxAdsAPIsOverride"),
            Flag.commandLine(AwSwitches.WEBVIEW_SHADOW_DOM_FENCED_FRAMES,
                    "Enables ShadowDOM-based fenced frames. Also implies SharedStorageAPI, "
                            + "and PrivacySandboxAdsAPIsOverride"),
            Flag.commandLine(AwSwitches.WEBVIEW_DISABLE_APP_RECOVERY,
                    "Disables WebView from checking for app recovery mitigations."),
            Flag.commandLine(AwSwitches.WEBVIEW_ENABLE_APP_RECOVERY,
                    "Enables WebView to check for app recovery mitigations."),
            Flag.baseFeature(GpuFeatures.WEBVIEW_VULKAN,
                    "Use Vulkan for composite. Requires Android device and OS support. May crash "
                            + "if enabled on unsupported device."),
            Flag.baseFeature(GpuFeatures.WEBVIEW_SURFACE_CONTROL,
                    "Use SurfaceControl. Requires WebViewThreadSafeMedia and Android device and OS "
                            + "support."),
            Flag.baseFeature(GpuFeatures.WEBVIEW_THREAD_SAFE_MEDIA,
                    "Use thread-safe media path, requires Android P."),
            Flag.baseFeature(VizFeatures.WEBVIEW_NEW_INVALIDATE_HEURISTIC,
                    "More robust heuristic for calling Invalidate"),
            Flag.baseFeature(
                    VizFeatures.WEBVIEW_VULKAN_INTERMEDIATE_BUFFER, "For debugging vulkan"),
            Flag.baseFeature(
                    GpuFeatures.USE_GLES2_FOR_OOP_R, "Force Skia context to use es2 only."),
            Flag.baseFeature(AwFeatures.WEBVIEW_CONNECTIONLESS_SAFE_BROWSING,
                    "Uses GooglePlayService's 'connectionless' APIs for Safe Browsing "
                            + "security checks."),
            Flag.baseFeature(AwFeatures.WEBVIEW_BROTLI_SUPPORT,
                    "Enables brotli compression support in WebView."),
            Flag.baseFeature(AwFeatures.WEBVIEW_EXTRA_HEADERS_SAME_ORIGIN_ONLY,
                    "Only allow extra headers added via loadUrl() to be sent to the same origin "
                            + "as the original request."),
            Flag.baseFeature(AwFeatures.WEBVIEW_MEASURE_SCREEN_COVERAGE,
                    "Measure the number of pixels occupied by one or more WebViews as a proportion "
                            + "of the total screen size. Depending on the number of WebViews and "
                            + "the size of the screen this might be expensive so hidden behind a "
                            + "feature flag until the true runtime cost can be measured."),
            Flag.baseFeature(AwFeatures.WEBVIEW_DISPLAY_CUTOUT,
                    "Enables display cutout (notch) support in WebView for Android P and above."),
            Flag.baseFeature(BlinkFeatures.WEBVIEW_ACCELERATE_SMALL_CANVASES,
                    "Accelerate all canvases in webview."),
            Flag.baseFeature(AwFeatures.WEBVIEW_MIXED_CONTENT_AUTOUPGRADES,
                    "Enables autoupgrades for audio/video/image mixed content when mixed content "
                            + "mode is set to MIXED_CONTENT_COMPATIBILITY_MODE"),
            Flag.baseFeature(AwFeatures.WEBVIEW_JAVA_JS_BRIDGE_MOJO,
                    "Enables the new Java/JS Bridge code path with mojo implementation."),
            Flag.baseFeature(
                    AwFeatures.WEBVIEW_ORIGIN_TRIALS, "Enables Origin Trials support on WebView."),
            Flag.baseFeature(BlinkFeatures.GMS_CORE_EMOJI,
                    "Enables retrieval of the emoji font through GMS Core "
                            + "improving emoji glyph coverage."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_ACROSS_IFRAMES,
                    "Enable Autofill for frame-transcending forms (forms whose fields live in "
                            + "different frames)."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_COUNTRY_FROM_LOCAL_NAME,
                    "Chrome needs to map country names to country codes. If enabled, the lookup "
                            + "considers all locales that are registered for a country."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_ENABLE_DEPENDENT_LOCALITY_PARSING,
                    "Enables parsing dependent locality fields (e.g. Bairros in Brazil)."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_ENABLE_RANKING_FORMULA,
                    "Enables new autofill suggestion ranking formula"),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_ENFORCE_DELAYS_IN_STRIKE_DATABASE,
                    "Enforce delay between offering Autofill opportunities in the "
                            + "strike database."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_PARSE_ASYNC,
                    "Parse forms asynchronously outside of the UI thread."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_PARSING_PATTERN_PROVIDER,
                    "Enables Autofill to use its new method to retrieve parsing patterns."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_PAGE_LANGUAGE_DETECTION,
                    "Enables Autofill to retrieve the page language for form parsing."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_ENABLE_SENDING_BCN_IN_GET_UPLOAD_DETAILS,
                    "Enables sending billing customer number in GetUploadDetails."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_PARSE_MERCHANT_PROMO_CODE_FIELDS,
                    "When enabled, Autofill will attempt to find merchant promo/coupon/gift code "
                            + "fields when parsing forms."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_RATIONALIZE_STREET_ADDRESS_AND_ADDRESS_LINE,
                    "Rationalizes (street address, address line 2) field sequences to "
                            + "(address line1, address line 2)."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_FILL_CREDIT_CARD_AS_PER_FORMAT_STRING,
                    "When enabled, Autofill tries to infer the credit card expiry date format "
                            + "from the label and placeholder."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_CONSIDER_PLACEHOLDER_FOR_PARSING,
                    "When enabled, Autofill local heuristics consider the placeholder attribute "
                            + "for determining field types."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_IMPROVED_LABEL_FOR_INFERENCE,
                    "When enabled, Autofill associates assigned labels with inputs in unowned forms."),
            Flag.baseFeature(AutofillFeatures.AUTOFILL_SERVER_BEHAVIORS,
                    "When enabled, Autofill will request experimental "
                            + "predictions from the Autofill API."),
            Flag.baseFeature(FeatureConstants.KEYBOARD_ACCESSORY_PAYMENT_VIRTUAL_CARD_FEATURE,
                    "When enabled, merchant bound virtual cards will be offered in the keyboard "
                            + "accessory."),
            Flag.baseFeature(
                    NetworkServiceFeatures.TRUST_TOKENS, "Enables the prototype Trust Tokens API."),
            Flag.commandLine(AwSwitches.WEBVIEW_DISABLE_PACKAGE_ALLOWLIST_THROTTLING,
                    "Disables throttling querying apps package names allowlist components in"
                            + "WebView clients."),
            Flag.baseFeature(AwFeatures.WEBVIEW_EMPTY_COMPONENT_LOADER_POLICY,
                    "Enables loading a fake empty (no-op) component during WebView startup."),
            Flag.commandLine(AwSwitches.WEBVIEW_SELECTIVE_IMAGE_INVERSION_DARKENING,
                    "Enables use selective image inversion to automatically darken page, it will be"
                            + " used when WebView is in dark mode, but website doesn't provide dark"
                            + " style."),
            Flag.baseFeature(AwFeatures.WEBVIEW_FORCE_DARK_MODE_MATCH_THEME,
                    "Automatically darken page if"
                            + " WebView is set to FORCE_DARK_AUTO and the app has dark theme"),
            Flag.baseFeature(ContentFeatures.VERIFY_DID_COMMIT_PARAMS,
                    "Enables reporting of browser and renderer navigation inconsistencies on"
                            + "navigations"),
            Flag.baseFeature(ContentFeatures.USER_MEDIA_CAPTURE_ON_FOCUS,
                    "Enables GetUserMedia API will only resolve when the document calling it has"
                            + "focus"),
            Flag.baseFeature(ContentFeatures.COMPOSITE_BG_COLOR_ANIMATION,
                    "When enabled, the background-color animation runs on the compositor thread."),
            Flag.baseFeature(AwFeatures.WEBVIEW_USE_METRICS_UPLOAD_SERVICE,
                    "Upload UMA metrics logs through MetricsUploadService not via GMS-core"
                            + " directly."),
            Flag.baseFeature(BlinkFeatures.FORCE_MAJOR_VERSION_IN_MINOR_POSITION_IN_USER_AGENT,
                    "Force the Chrome major version number to 99 and put the major version"
                            + " number in the minor version position in the User-Agent string."),
            Flag.baseFeature(NetworkServiceFeatures.URL_LOADER_SYNC_CLIENT,
                    "Optimizes communication between URLLoader and CorsURLLoader."),
            Flag.baseFeature(NetworkServiceFeatures.FASTER_SET_COOKIE, "Optimizes cookie access."),
            Flag.baseFeature(NetFeatures.OPTIMIZE_NETWORK_BUFFERS,
                    "Optimizes buffer size for reading from the network or InputStream."),
            Flag.baseFeature(BlinkFeatures.SET_TIMEOUT_WITHOUT_CLAMP,
                    "Enables faster setTimeout(,0) by removing the 1 ms clamping."),
            Flag.baseFeature(BlinkFeatures.PAINT_HOLDING_CROSS_ORIGIN,
                    "Defers the first commit until FCP or timeout for cross-origin navigations."),
            Flag.baseFeature(BlinkFeatures.EARLY_CODE_CACHE,
                    "Enables fetching the code cache earlier in navigation."),
            Flag.baseFeature(BlinkFeatures.HTML_PARAM_ELEMENT_URL_SUPPORT,
                    "Enables the <param> element's URL-setting features (this functionality is"
                            + " being deprecated and removed, so ENABLED is the safe/current"
                            + " behavior, and DISABLED is the tested/new behavior)."),
            Flag.baseFeature(ContentFeatures.NAVIGATION_REQUEST_PRECONNECT,
                    "Enables preconnecting for frame requests."),
            Flag.baseFeature(ContentFeatures.NAVIGATION_NETWORK_RESPONSE_QUEUE,
                    "Schedules tasks related to the navigation network responses on a higher "
                            + "priority task queue."),
            Flag.baseFeature(ContentFeatures.FONT_MANAGER_EARLY_INIT,
                    "Whether to initialize the font manager when the renderer starts on a "
                            + "background thread."),
            Flag.baseFeature(ContentFeatures.TREAT_BOOTSTRAP_AS_DEFAULT,
                    "Executes tasks with  the kBootstrap task type on the default task queues "
                            + "(based on priority of the task) rather than a dedicated "
                            + "high-priority task queue."),
            Flag.baseFeature(BlinkFeatures.PREFETCH_ANDROID_FONTS,
                    "Enables prefetching Android fonts on renderer startup."),
            Flag.baseFeature(AwFeatures.WEBVIEW_LEGACY_TLS_SUPPORT,
                    "Whether legacy TLS versions (TLS 1.0/1.1) conections are allowed."),
            Flag.baseFeature(BlinkFeatures.INITIAL_NAVIGATION_ENTRY,
                    "Enables creation of initial NavigationEntries on WebContents creation."),
            Flag.baseFeature(BlinkFeatures.CANVAS2D_STAYS_GPU_ON_READBACK,
                    "Accelerated canvases that a read back from remain accelerated."),
            Flag.baseFeature(BlinkFeatures.EARLY_BODY_LOAD,
                    "Enables loading the response body earlier in navigation."),
            Flag.baseFeature(BlinkFeatures.DEFAULT_STYLE_SHEETS_EARLY_INIT,
                    "Initialize CSSDefaultStyleSheets early in renderer startup."),
            Flag.baseFeature(ContentFeatures.THREADING_OPTIMIZATIONS_ON_IO,
                    "Moves navigation threading optimizations to the IO thread."),
            Flag.baseFeature(ContentFeatures.EARLY_ESTABLISH_GPU_CHANNEL,
                    "Enable establishing the GPU channel early in renderer startup."),
            Flag.baseFeature(ContentFeatures.OPTIMIZE_EARLY_NAVIGATION,
                    "Temporarily pauses the compositor early in navigation."),
            Flag.baseFeature(ContentFeatures.INCLUDE_IPC_OVERHEAD_IN_NAVIGATION_START,
                    "Whether navigation metrics include ipc overhead."),
            Flag.baseFeature(ContentFeatures.AVOID_UNNECESSARY_BEFORE_UNLOAD_CHECK_POST_TASK,
                    "Avoids an unnecessary renderer ipc during navigation for before-unload "
                            + "handlers."),
            Flag.baseFeature(AwFeatures.WEBVIEW_X_REQUESTED_WITH_HEADER_CONTROL,
                    "Restricts insertion of XRequestedWith header on outgoing requests "
                            + "to those that have been allow-listed through the appropriate "
                            + "developer API."),
            Flag.baseFeature(
                    AwFeatures.WEBVIEW_SYNTHESIZE_PAGE_LOAD_ONLY_ON_INITIAL_MAIN_DOCUMENT_ACCESS,
                    "Only synthesize page load for URL spoof prevention at most once,"
                            + " on initial main document access."),
            Flag.baseFeature(BlinkFeatures.VIEWPORT_HEIGHT_CLIENT_HINT_HEADER,
                    "Enables the use of sec-ch-viewport-height client hint."),
            Flag.baseFeature(BlinkFeatures.USER_AGENT_OVERRIDE_EXPERIMENT,
                    "Collects metrics on when the User-Agent string is overridden and how"),
            Flag.baseFeature(GpuFeatures.CANVAS_CONTEXT_LOST_IN_BACKGROUND,
                    "Free Canvas2D resources when the webview is in the background."),
            Flag.baseFeature(VizFeatures.SURFACE_SYNC_THROTTLING,
                    "Enables throttling of Surface Sync to improve rotations"),
            Flag.baseFeature(BlinkFeatures.AUTOFILL_SHADOW_DOM,
                    "Enables Autofill associate form elements with form "
                            + "control elements across shadow boundaries."),
            Flag.baseFeature(BlinkFeatures.UACH_OVERRIDE_BLANK,
                    "Changes behavior of User-Agent Client Hints to send blank headers "
                            + "when the User-Agent string is overriden"),
            Flag.baseFeature(BlinkFeatures.MAX_UNTHROTTLED_TIMEOUT_NESTING_LEVEL,
                    "Increases the nesting threshold before which "
                            + "setTimeout(..., <4ms) starts being clamped to 4 ms."),
            Flag.baseFeature(BlinkFeatures.ESTABLISH_GPU_CHANNEL_ASYNC,
                    "Enables establishing the GPU channel asnchronously when requesting a new "
                            + "layer tree frame sink."),
            Flag.baseFeature(BlinkFeatures.DECODE_SCRIPT_SOURCE_OFF_THREAD,
                    "If enabled, script source text will be decoded and hashed off the main"
                            + "thread."),
            Flag.baseFeature(BaseFeatures.OPTIMIZE_DATA_URLS,
                    "Optimizes parsing and loading of data: URLs."),
            Flag.baseFeature(BlinkFeatures.EVENT_PATH, "Enables the deprecated Event.path API."),
            Flag.baseFeature(BlinkFeatures.PREFETCH_FONT_LOOKUP_TABLES,
                    "If enabled, font lookup tables will be prefetched on renderer startup."),
            Flag.baseFeature(BlinkFeatures.PRECOMPILE_INLINE_SCRIPTS,
                    "If enabled, inline scripts will be stream compiled using a background HTML"
                            + " scanner."),
            Flag.baseFeature(BaseFeatures.RUN_TASKS_BY_BATCHES,
                    "Run tasks in queue for 8ms before before sending a system message."),
            Flag.baseFeature(BlinkFeatures.OFFSET_PARENT_NEW_SPEC_BEHAVIOR,
                    "Enables new HTMLElement.offsetParent behavior to match other browsers."),
            Flag.baseFeature(AwFeatures.WEBVIEW_RECORD_APP_DATA_DIRECTORY_SIZE,
                    "Record the size of the embedding app's data directory"),
            Flag.baseFeature(BlinkFeatures.EARLY_EXIT_ON_NOOP_CLASS_OR_STYLE_CHANGE,
                    "Early exit when the style or class attribute of a DOM element is set to the"
                            + " same value as before."),
            Flag.baseFeature(BlinkFeatures.THREADED_PRELOAD_SCANNER,
                    "If enabled, the HTMLPreloadScanner will run on a worker thread."),
            Flag.baseFeature(BlinkFeatures.TIMED_HTML_PARSER_BUDGET,
                    "If enabled, the HTMLDocumentParser will use a budget based on elapsed time"
                            + " rather than token count."),
            Flag.baseFeature(AwFeatures.WEBVIEW_HIT_TEST_IN_BLINK_ON_TOUCH_START,
                    "Hit test on touch start in blink"),
            Flag.baseFeature(BlinkFeatures.STYLUS_WRITING_TO_INPUT,
                    "Allows using a stylus for text input. This requires "
                            + "either a Samsung device with Direct Writing enabled, or an "
                            + "Android T device with stylus handwriting enabled."),
            Flag.baseFeature(BlinkFeatures.SCROLL_UPDATE_OPTIMIZATIONS,
                    "Enable scroll update optimizations. See https://crbug.com/1346789."),
            Flag.baseFeature(BaseFeatures.ALIGN_WAKE_UPS, "Align delayed wake ups at 125 Hz"),
            Flag.baseFeature(BlinkSchedulerFeatures.THREADED_SCROLL_PREVENT_RENDERING_STARVATION,
                    "Enable rendering starvation-prevention during threaded scrolling."
                            + " See https://crbug.com/1315279."),
            Flag.baseFeature(BaseFeatures.NO_WAKE_UPS_FOR_CANCELED_TASKS,
                    "Controls whether wake ups are possible for canceled tasks."),
            Flag.baseFeature(BaseFeatures.REMOVE_CANCELED_TASKS_IN_TASK_QUEUE,
                    "Controls whether or not canceled delayed tasks are removed from task queues."),
            Flag.baseFeature(BaseFeatures.ALWAYS_ABANDON_SCHEDULED_TASK,
                    "Controls whether or not the scheduled task is always abandoned when a timer "
                            + "is stopped or resets."),
            Flag.baseFeature(BlinkFeatures.THROTTLE_INTERSECTION_OBSERVER_UMA,
                    "Reduces fine-grained UMA metrics for IntersectionObserver."),
            Flag.baseFeature(BlinkFeatures.PRETOKENIZE_CSS,
                    "If enabled, CSS will be tokenized in a background thread when possible."),
            Flag.baseFeature(BlinkFeatures.DOCUMENT_TRANSITION,
                    "Enables the experimental Shared Element Transitions API."
                            + " See https://github.com/WICG/shared-element-transitions/blob/main/explainer.md."),
            Flag.baseFeature(BlinkFeatures.CSS_OVERFLOW_FOR_REPLACED_ELEMENTS,
                    "Enables respecting the CSS overflow property on replaced elements."
                            + " See https://chromestatus.com/feature/5137515594383360."),
            Flag.baseFeature(GpuFeatures.INCREASED_CMD_BUFFER_PARSE_SLICE,
                    "Enable the use of an increased parse slice size per command buffer before"
                            + " each forced context switch."),
            Flag.baseFeature(MetricsFeatures.CONSOLIDATE_METRICS_SERVICE_INITIAL_LOG_LOGIC,
                    "Controls whether the logic to build the initial UMA log is the same as"
                            + " other logs."),
            Flag.baseFeature(AccessibilityFeatures.ABLATE_SEND_PENDING_ACCESSIBILITY_EVENTS,
                    "Enable to increase the cost of SendPendingAccessibilityEvents"),
            Flag.baseFeature(ContentFeatures.WEBVIEW_THROTTLE_BACKGROUND_BEGIN_FRAME,
                    "Enable to throttle begin frames when webview is not being drawn"),
            Flag.baseFeature(BlinkFeatures.RUN_TEXT_INPUT_UPDATE_POST_LIFECYCLE,
                    "Runs code to update IME state at the end of a lifecycle update "
                            + "rather than the beginning."),
            Flag.baseFeature(CcFeatures.NON_BLOCKING_COMMIT,
                    "Don't block the renderer main thread unconditionally while waiting "
                            + "for commit to finish on the compositor thread."),
            Flag.baseFeature(AwFeatures.WEBVIEW_CLIENT_HINTS_CONTROLLER_DELEGATE,
                    "This persists client hints between top-level navigations."),
            Flag.baseFeature(CcFeatures.USE_DMSAA_FOR_TILES,
                    "Switches skia to use DMSAA instead of MSAA for tile raster"),
            Flag.baseFeature(
                    CcFeatures.AVOID_RASTER_DURING_ELASTIC_OVERSCROLL, "No effect on webview"),
            Flag.baseFeature(BlinkFeatures.DOCUMENT_EVENT_NODE_PATH_CACHING,
                    "Enables a performance optimization that caches event paths."),
            Flag.baseFeature(BlinkFeatures.WEB_RTC_METRONOME,
                    "Inject a metronome into webrtc to allow task coalescing, "
                            + " including synchronized decoding."),
            Flag.baseFeature(BlinkFeatures.THREADED_BODY_LOADER,
                    "If enabled, reads and decodes navigation body data off the main thread."),
            Flag.baseFeature("PreconnectOnRedirect"),
            // Add new commandline switches and features above. The final entry should have a
            // trailing comma for cleaner diffs.
    };
}
