// Copyright 2017 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ios/chrome/browser/flags/ios_chrome_flag_descriptions.h"

// This file declares strings used in chrome://flags. These messages are not
// translated, because instead of end-users they target Chromium developers and
// testers. See https://crbug.com/587272 and https://crbug.com/703134 for more
// details.

namespace flag_descriptions {

const char kAdaptiveSuggestionsCountName[] = "Omnibox adaptive suggestions";
const char kAdaptiveSuggestionsCountDescription[] =
    "Allows Omnibox to dynamically adjust number of offered suggestions to "
    "fill in the space between Omnibox and the soft keyboard.";

const char kAppStoreRatingName[] = "Enable the App Store Rating promo.";
const char kAppStoreRatingDescription[] =
    "When enabled, App Store Rating promo will be presented to eligible "
    "users.";

const char kAutofillBrandingIOSName[] = "Autofill Branding on iOS";
const char kAutofillBrandingIOSDescription[] =
    "Adds the Chrome logo in the form input suggestions bar. Full color by "
    "default.";

const char kAutofillCreditCardUploadName[] =
    "Offers uploading Autofilled credit cards";
const char kAutofillCreditCardUploadDescription[] =
    "Offers uploading Autofilled credit cards to Google Payments after form "
    "submission.";

const char kAutofillEnableNewCardUnmaskPromptViewName[] =
    "Enable the new Card Unmask Prompt View for Autofill.";
const char kAutofillEnableNewCardUnmaskPromptViewDescription[] =
    "Displays the new autofill prompt for entering a credit card's CVC and "
    "(optional) expiration date.";

const char kAutofillEnableRankingFormulaName[] =
    "Enable new Autofill suggestion ranking formula";
const char kAutofillEnableRankingFormulaDescription[] =
    "When enabled, Autofill will use a new ranking formula to rank Autofill "
    "data model suggestions such as credit cards or profiles";

const char kAutofillEnableRemadeDownstreamMetricsName[] =
    "Enable remade Autofill Downstream metrics logging";
const char kAutofillEnableRemadeDownstreamMetricsDescription[] =
    "When enabled, some extra metrics logging for Autofill Downstream will "
    "start.";

const char kAutofillEnableSendingBcnInGetUploadDetailsName[] =
    "Enable sending billing customer number in GetUploadDetails";
const char kAutofillEnableSendingBcnInGetUploadDetailsDescription[] =
    "When enabled the billing customer number will be sent in the "
    "GetUploadDetails preflight calls.";

const char kAutofillEnableUnmaskCardRequestSetInstrumentIdName[] =
    "When enabled, sets non-legacy instrument ID in UnmaskCardRequest";
const char kAutofillEnableUnmaskCardRequestSetInstrumentIdDescription[] =
    "When enabled, UnmaskCardRequest will set the card's non-legacy ID when "
    "available.";

const char kAutofillEnableCardProductNameName[] =
    "Enable showing card product name";
const char kAutofillEnableCardProductNameDescription[] =
    "When enabled, card product name (instead of issuer network) will be shown "
    "in Payments UI.";

const char kAutofillEnforceDelaysInStrikeDatabaseName[] =
    "Enforce delay between offering Autofill opportunities in the strike "
    "database";
const char kAutofillEnforceDelaysInStrikeDatabaseDescription[] =
    "When enabled, if previous Autofill feature offer was declined, "
    "Chrome will wait for sometime before showing the offer again.";

const char kAutofillFillMerchantPromoCodeFieldsName[] =
    "Enable Autofill of promo code fields in forms";
const char kAutofillFillMerchantPromoCodeFieldsDescription[] =
    "When enabled, Autofill will attempt to fill merchant promo/coupon/gift "
    "code fields when data is available.";

const char kAutofillIOSDelayBetweenFieldsName[] = "Autofill delay";
const char kAutofillIOSDelayBetweenFieldsDescription[] =
    "Delay between the different fields of a form being autofilled. In "
    "milliseconds.";

const char kAutofillParseIBANFieldsName[] = "Parse IBAN fields in forms";
const char kAutofillParseIBANFieldsDescription[] =
    "When enabled, Autofill will attempt to find International Bank Account "
    "Number (IBAN) fields when parsing forms.";

const char kAutofillParseMerchantPromoCodeFieldsName[] =
    "Parse promo code fields in forms";
const char kAutofillParseMerchantPromoCodeFieldsDescription[] =
    "When enabled, Autofill will attempt to find merchant promo/coupon/gift "
    "code fields when parsing forms.";

const char kAutofillParseVcnCardOnFileStandaloneCvcFieldsName[] =
    "Parse standalone CVC fields for VCN card on file in forms";
const char kAutofillParseVcnCardOnFileStandaloneCvcFieldsDescription[] =
    "When enabled, Autofill will attempt to find standalone CVC fields for VCN "
    "card on file when parsing forms.";

const char kAutofillPruneSuggestionsName[] = "Autofill Prune Suggestions";
const char kAutofillPruneSuggestionsDescription[] =
    "Further limits the number of suggestions in the Autofill dropdown.";

const char kAutofillSaveCardDismissOnNavigationName[] =
    "Save Card Dismiss on Navigation";
const char kAutofillSaveCardDismissOnNavigationDescription[] =
    "Dismisses the Save Card Infobar on a user initiated Navigation, other "
    "than one caused by submitted form.";

const char kAutofillUpstreamAllowAdditionalEmailDomainsName[] =
    "Allow Autofill credit card upload save for select non-Google-based "
    "accounts";
const char kAutofillUpstreamAllowAdditionalEmailDomainsDescription[] =
    "When enabled, credit card upload is offered if the user's logged-in "
    "account's domain is from a common email provider.";

const char kAutofillUpstreamAllowAllEmailDomainsName[] =
    "Allow Autofill credit card upload save for all non-Google-based accounts";
const char kAutofillUpstreamAllowAllEmailDomainsDescription[] =
    "When enabled, credit card upload is offered without regard to the user's "
    "logged-in account's domain.";

const char kAutofillUseMobileLabelDisambiguationName[] =
    "Autofill Uses Mobile Label Disambiguation";
const char kAutofillUseMobileLabelDisambiguationDescription[] =
    "When enabled, Autofill suggestions' labels are displayed using a "
    "mobile-friendly format.";

const char kAutofillUseRendererIDsName[] =
    "Autofill logic uses unqiue renderer IDs";
const char kAutofillUseRendererIDsDescription[] =
    "When enabled, Autofill logic uses unique numeric renderer IDs instead "
    "of string form and field identifiers in form filling logic.";

const char kBreakpadNoDelayInitialUploadName[] =
    "Remove delay on initial crash upload";
const char kBreakpadNoDelayInitialUploadDescription[] =
    "When enabled, the initial crash uploading will not be delayed. When "
    "disabled, initial upload is delayed until deferred initialization. This "
    "does not affect recovery mode.";

const char kBubbleRichIPHName[] = "Bubble rich IPH";
const char kBubbleRichIPHDescription[] =
    "When enabled, displays a rich description (ex: title, image, etc..) of "
    "the feature presented in the bubble. Also enables password suggestion "
    "highlight IPH. When enabled with no option, uses the default bubble "
    "style.";

extern const char kCalendarExperienceKitName[] = "Experience Kit Calendar";
extern const char kCalendarExperienceKitDescription[] =
    "When enabled, long pressing on dates will trigger Experience Kit Calendar "
    "event handling";

extern const char kEnableExpKitCalendarTextClassifierName[] =
    "Text Classifier in Experience Kit Calendar";
extern const char kEnableExpKitCalendarTextClassifierDescription[] =
    "When enabled, Experience Kit Calendar will use Text Classifier library in "
    "entity detection where possible.";

extern const char kMapsExperienceKitName[] = "Experience Kit Maps";
extern const char kMapsExperienceKitDescription[] =
    "When enabled, long pressing on an address will trigger Experience Kit Maps"
    "location and directions handling. Requires "
    "#enable-long-press-surrounding-text to be enabled";

extern const char kLongPressSurroundingTextName[] =
    "Enable Long Press Surrounding Text";
extern const char kLongPressSurroundingTextDescription[] =
    "When enabled, long pressing a text will analyze larger part of the text.";

const char kContentSuggestionsUIModuleRefreshName[] =
    "Content Suggestions UI Module Refresh";
const char kContentSuggestionsUIModuleRefreshDescription[] =
    "When enabled, the Content Suggestions will be redesigned to be contained "
    "into distinct modules.";

const char kCrashpadIOSName[] = "Use Crashpad for crash collection.";
const char kCrashpadIOSDescription[] =
    "When enabled use Crashpad to generate crash reports crash collection. "
    "When disabled use Breakpad. This flag takes two restarts to take effect";

#if BUILDFLAG(DCHECK_IS_CONFIGURABLE)
const char kDcheckIsFatalName[] = "DCHECKs are fatal";
const char kDcheckIsFatalDescription[] =
    "By default Chrome will evaluate in this build, but only log failures, "
    "rather than crashing. If enabled, DCHECKs will crash the calling process.";
#endif  // BUILDFLAG(DCHECK_IS_CONFIGURABLE)

const char kDefaultBrowserFullscreenPromoExperimentName[] =
    "Default Browser Fullscreen modal experiment";
const char kDefaultBrowserFullscreenPromoExperimentDescription[] =
    "When enabled, will show a modified default browser fullscreen modal promo "
    "UI.";

const char kDefaultBrowserIntentsShowSettingsName[] =
    "Default Browser Intents show settings";
const char kDefaultBrowserIntentsShowSettingsDescription[] =
    "When enabled, external apps can trigger the settings screen showing "
    "default browser tutorial.";

const char kDelayThresholdMinutesToUpdateGaiaCookieName[] =
    "Delay for polling (in minutes) to verify the existence of GAIA cookies.";
const char kDelayThresholdMinutesToUpdateGaiaCookieDescription[] =
    "Used for testing purposes to reduce the amount of delay between polling "
    "intervals.";

const char kDetectMainThreadFreezeName[] = "Detect freeze in the main thread.";
const char kDetectMainThreadFreezeDescription[] =
    "A crash report will be uploaded if the main thread is frozen more than "
    "the time specified by this flag.";

const char kDiscoverFeedInNtpName[] = "Enable new content Suggestion Feed";
const char kDiscoverFeedInNtpDescription[] =
    "When enabled, replaces articles feed with new content Suggestion Feed in "
    "the NTP.";

const char kEnableDiscoverFeedTopSyncPromoName[] =
    "Enable the sync promo on top of the feed.";
const char kEnableDiscoverFeedTopSyncPromoDescription[] =
    "When enabled, a sync promotion will be presented to eligible users on top "
    "of the feed cards.";

const char kDmTokenDeletionName[] = "DMToken deletion";
const char kDmTokenDeletionDescription[] =
    "Delete the corresponding DMToken when a managed browser is deleted in "
    "Chrome Browser Cloud Management.";

const char kEditPasswordsInSettingsName[] = "Edit passwords in settings";
const char kEditPasswordsInSettingsDescription[] =
    "Enables password editing in settings.";

const char kEnableAutofillAddressSavePromptAddressVerificationName[] =
    "Autofill Address Save Prompts Address Verification";
const char kEnableAutofillAddressSavePromptAddressVerificationDescription[] =
    "Enable the address verification support in Autofill address save prompts.";

const char kEnableAutofillAddressSavePromptName[] =
    "Autofill Address Save Prompts";
const char kEnableAutofillAddressSavePromptDescription[] =
    "Enable the Autofill address save prompts.";

const char kEnableCBDSignOutName[] = "Enable Clear Browsing Data Sign-out";
const char kEnableCBDSignOutDescription[] =
    "Offer signed-in user to sign-out from Clear Browsing Data settings.";

const char kEnableCheckVisibilityOnAttentionLogStartName[] =
    "Enable Check Feed Visibility On Attention Log Start";
const char kEnableCheckVisibilityOnAttentionLogStartDescription[] =
    "Enable checking feed visibility on attention log start.";

const char kEnableDiscoverFeedDiscoFeedEndpointName[] =
    "Enable discover feed discofeed";
const char kEnableDiscoverFeedDiscoFeedEndpointDescription[] =
    "Enable using the discofeed endpoint for the discover feed.";

const char kEnableDiscoverFeedPreviewName[] = "Enable discover feed preview";
const char kEnableDiscoverFeedPreviewDescription[] =
    "Enable showing a live preview for discover feed long-press menu.";

const char kEnableDiscoverFeedGhostCardsName[] =
    "Enable discover feed ghost cards";
const char kEnableDiscoverFeedGhostCardsDescription[] =
    "Show ghost cards when refreshing the discover feed.";

const char kEnableDiscoverFeedStaticResourceServingName[] =
    "Enable discover feed static resource serving";
const char kEnableDiscoverFeedStaticResourceServingDescription[] =
    "When enabled the discover feed will optimize the request of resources "
    "coming from the server.";

const char kEnableFREDefaultBrowserPromoScreenName[] =
    "Enable FRE default browser screen";
const char kEnableFREDefaultBrowserPromoScreenDescription[] =
    "Display the FRE default browser screen and other default browser promo "
    "depending on experiment.";

const char kEnableFaviconForPasswordsName[] =
    "Enable favicons for the Password Manager and for the Credential Provider "
    "Extension";
const char kEnableFaviconForPasswordsDescription[] =
    "Show favicons in the Password Manager settings for the Saved Passwords "
    "and Never Saved sections and also for the AutoFill Passwords for the "
    "Credential Provider.";

const char kEnableFeedAblationName[] = "Enables Feed Ablation";
const char kEnableFeedAblationDescription[] =
    "If Enabled the Feed will be removed from the NTP";

const char kEnableFREUIModuleIOSName[] = "Enable FRE UI module";
const char kEnableFREUIModuleIOSDescription[] =
    "Use the new FRE UI module for first run.";

const char kEnableFullscreenAPIName[] = "Enable Fullscreen API";
const char kEnableFullscreenAPIDescription[] =
    "Enable the Fullscreen API for web content (iOS 16.0+).";

const char kPasswordsGroupingName[] =
    "Enable password grouping for the Password Manager";
const char kPasswordsGroupingDescription[] =
    "Group passwords into the same affiliated group in the Password Manager "
    "for the Saved Passwords section";

const char kEnableOpenInDownloadName[] = "Enable Open In download";
const char kEnableOpenInDownloadDescription[] =
    "Enable new download for Open In menu (iOS 14.5+).";

const char kEnableRefineDataSourceReloadReportingName[] =
    "Enable Refine Data Source Reload Reporting";
const char kEnableRefineDataSourceReloadReportingDescription[] =
    "Enable refining data source reload reporting when having a very short "
    "attention log";

const char kEnableUnicornAccountSupportName[] =
    "Enable Unicorn account support";
const char kEnableUnicornAccountSupportDescription[] =
    "Allows users to sign-in with their Unicorn account.";

const char kEnableWebPageAnnotationsName[] = "Enable Web Page Intent Detection";
const char kEnableWebPageAnnotationsDescription[] =
    "Prototype to detect and highlight data with possible intent in a web "
    "page.";

const char kEnableSuggestionsScrollingOnIPadName[] =
    "Enable omnibox suggestions scrolling on iPad";
const char kEnableSuggestionsScrollingOnIPadDescription[] =
    "Enable omnibox suggestions scrolling on iPad and disable suggestions "
    "hiding on keyboard dismissal.";

const char kEnableUserPolicyName[] = "Enable user policies";
const char kEnableUserPolicyDescription[] =
    "Enable the fetch and application of user policies when synced with a "
    "managed account";

const char kEnableWebChannelsName[] = "Enable WebFeed";
const char kEnableWebChannelsDescription[] =
    "Enable folowing content from web and display Following feed on NTP based "
    "on sites that users followed.";

const char kEnhancedProtectionName[] = "Enable Enhanced Safe Browsing";
const char kEnhancedProtectionDescription[] =
    "Allows users to opt-in to Enhanced Safe Browsing";

const char kEnhancedProtectionPhase2Name[] =
    "Enable Enhanced Safe Browsing Phase 2";
const char kEnhancedProtectionPhase2Description[] =
    "Enable additional improvements related to Enhanced Safe Browsing";

const char kTailoredSecurityIntegrationName[] =
    "Enable Tailored Security Integration";
const char kTailoredSecurityIntegrationDescription[] =
    "Enable integration between account level enhanced safe browsing and "
    "chrome enhanced safe browsing";

const char kExpandedTabStripName[] = "Enable expanded tabstrip";
const char kExpandedTabStripDescription[] =
    "Enables the new expanded tabstrip. Activated by swiping down the tabstrip"
    " or the toolbar";

const char kFeedBackgroundRefreshName[] = "Enable feed background refresh";
const char kFeedBackgroundRefreshDescription[] =
    "Schedules a feed background refresh after some minimum period of time has "
    "passed after the last refresh.";

const char kFillingAcrossAffiliatedWebsitesName[] =
    "Fill passwords across affiliated websites.";
const char kFillingAcrossAffiliatedWebsitesDescription[] =
    "Enables filling password on a website when there is saved "
    "password on affiliated website.";

const char kFollowingFeedDefaultSortTypeName[] =
    "Following feed default sort type.";
const char kFollowingFeedDefaultSortTypeDescription[] =
    "Sets the default sort type for Following feed content.";

const char kForceStartupSigninPromoName[] = "Display the startup sign-in promo";
const char kForceStartupSigninPromoDescription[] =
    "When enabled, the startup sign-in promo is always displayed when starting "
    "Chrome.";

const char kFullscreenPromosManagerName[] = "Fullscreen Promos Manager";
const char kFullscreenPromosManagerDescription[] =
    "When enabled, the display of fullscreen promos will be coordinated by a "
    "central manager living at the application level.";

const char kFullscreenPromosManagerSkipInternalLimitsName[] =
    "Fullscreen Promos Manager (Skip internal Impression Limits)";
const char kFullscreenPromosManagerSkipInternalLimitsDescription[] =
    "When enabled, the internal Impression Limits of the Promos Manager will "
    "be ignored; this is useful for local development.";

const char kFullscreenSmoothScrollingName[] = "Fullscreen Smooth Scrolling";
const char kFullscreenSmoothScrollingDescription[] =
    "When enabled, the web view's insets are updated for scoll events. If "
    "disabled, the the web view's frame are updated.";

const char kHttpsOnlyModeName[] = "HTTPS-Only Mode Setting";
const char kHttpsOnlyModeDescription[] = "Enables the HTTPS-Only Mode setting";

const char kIncognitoNtpRevampName[] = "Revamped Incognito New Tab Page";
const char kIncognitoNtpRevampDescription[] =
    "When enabled, Incognito new tab page will have an updated UI.";

const char kInterestFeedV2ClickAndViewActionsConditionalUploadName[] =
    "New Content Suggestions taps/views conditional upload";
const char kInterestFeedV2ClickAndViewActionsConditionalUploadDescription[] =
    "Only enable the upload of taps/views after satisfying conditions (e.g., "
    "user views X cards)";

const char kInProductHelpDemoModeName[] = "In-Product Help Demo Mode";
const char kInProductHelpDemoModeDescription[] =
    "When enabled, in-product help promotions occur exactly once per cold "
    "start. Enabled causes all in-product help promotions to occur. Enabling "
    "an individual promotion causes that promotion but no other promotions to "
    "occur.";

const char kIOS3PIntentsInIncognitoName[] = "Third-party intents in Incognito";
const char kIOS3PIntentsInIncognitoDescription[] =
    "When enabled, an additional setting lets the user open links from other "
    "apps in Incognito.";

const char kIOSEnablePasswordManagerBrandingUpdateName[] =
    "Enable new Google Password Manager branding";
const char kIOSEnablePasswordManagerBrandingUpdateDescription[] =
    "Updates icons, strings, and views for Google Password Manager.";

const char kIOSNewPostRestoreExperienceName[] = "New Post Restore Experience";
const char kIOSNewPostRestoreExperienceDescription[] =
    "When enabled, a prompt will be presented after a device restore to "
    "allow the user to sign in again.";

const char kIOSPasswordUISplitName[] = "Password Manager UI Split";
const char kIOSPasswordUISplitDescription[] =
    "Splits Password Settings and "
    "Password Manager into two separate UIs.";

const char kIOSPasswordManagerCrossOriginIframeSupportName[] =
    "IOS Password Manager Cross-Origin Iframe Support";
const char kIOSPasswordManagerCrossOriginIframeSupportDescription[] =
    "Enables password saving and filling in cross-origin iframes on IOS.";

const char kIOSSharedHighlightingColorChangeName[] =
    "IOS Shared Highlighting color change";
const char kIOSSharedHighlightingColorChangeDescription[] =
    "Changes the Shared Highlighting color of the text fragment"
    "away from the default yellow in iOS. Works with #scroll-to-text-ios flag.";

const char kIOSSharedHighlightingAmpName[] = "Shared Highlighting on AMP pages";
const char kIOSSharedHighlightingAmpDescription[] =
    "Enables the Create Link option on AMP pages.";

const char kIOSSharedHighlightingV2Name[] = "Text Fragments UI improvements";
const char kIOSSharedHighlightingV2Description[] =
    "Enables improvements to text fragments UI, including a menu for removing "
    "or resharing a highlight.";

const char kKeyboardShortcutsMenuName[] = "Keyboard Shortcuts Menu";
const char kKeyboardShortcutsMenuDescription[] =
    "Enables the new keyboard shortcuts menu.";

const char kLockBottomToolbarName[] = "Lock bottom toolbar";
const char kLockBottomToolbarDescription[] =
    "When enabled, the bottom toolbar will not get collapsed when scrolling "
    "into fullscreen mode.";

const char kLogBreadcrumbsName[] = "Log Breadcrumb Events";
const char kLogBreadcrumbsDescription[] =
    "When enabled, breadcrumb events will be logged.";

const char kMediaPermissionsControlName[] =
    "Camera and Microphone Access Permissions Control";
const char kMediaPermissionsControlDescription[] =
    "Enables user control for camera and/or microphone access for a specific "
    "site through site settings during its lifespan.";

const char kMetrickitCrashReportName[] = "Metrickit crash reports";
const char kMetrickitCrashReportDescription[] =
    "Enables sending Metrickit crash reports";

const char kMetrickitNonCrashReportName[] = "Metrickit non-crash reports";
const char kMetrickitNonCrashReportDescription[] =
    "Enables sending Metrickit reports for non crash type (hang, "
    "cpu-exception, diskwrite-exception)";

const char kModernTabStripName[] = "Modern TabStrip";
const char kModernTabStripDescription[] =
    "When enabled, the newly implemented tabstrip can be tested.";

const char kMostVisitedTilesName[] = "Most Visited Tiles";
const char kMostVisitedTilesDescription[] =
    "Enables the most visited tiles in the omnibox. Shows most visited "
    "websites in a tile format when the user focuses the omnibox on a search "
    "result page (SRP) or on web.";

const char kNewMobileIdentityConsistencyFREName[] = "New MICE FRE";
const char kNewMobileIdentityConsistencyFREDescription[] =
    "New Mobile Identity Consistency FRE";

const char kNewOverflowMenuCBDActionName[] =
    "Clear Browsing Data action in new overflow menu";
const char kNewOverflowMenuCBDActionDescription[] =
    "Adds a Clear Browsing Data action to the new overflow menu";

const char kNewOverflowMenuName[] = "New Overflow Menu";
const char kNewOverflowMenuDescription[] = "Enables the new overflow menu";

const char kNewOverflowMenuAlternateIPHName[] =
    "New Overflow Menu Alternative IPH";
const char kNewOverflowMenuAlternateIPHDescription[] =
    "Uses the alternative IPH flow for the new overflow menu";

const char kNTPViewHierarchyRepairName[] = "NTP View Hierarchy Repair";
const char kNTPViewHierarchyRepairDescription[] =
    "Checks if NTP view hierarchy is broken and fixes it if necessary.";

const char kOmniboxFuzzyUrlSuggestionsName[] = "Omnibox Fuzzy URL Suggestions";
const char kOmniboxFuzzyUrlSuggestionsDescription[] =
    "Enables URL suggestions for inputs that may contain typos.";

const char kOmniboxHttpsUpgradesName[] = "Omnibox HTTPS upgrades";
const char kOmniboxHttpsUpgradesDescription[] =
    "Enables HTTPS upgrades for omnibox navigations typed without a scheme";

const char kOmniboxKeyboardPasteButtonName[] = "Omnibox keyboard paste button";
const char kOmniboxKeyboardPasteButtonDescription[] =
    "Enables paste button in the omnibox's keyboard accessory. Only available "
    "from iOS 16 onward.";

const char kOmniboxUIMaxAutocompleteMatchesName[] =
    "Omnibox UI Max Autocomplete Matches";
const char kOmniboxUIMaxAutocompleteMatchesDescription[] =
    "Changes the maximum number of autocomplete matches displayed in the "
    "Omnibox UI.";

const char kOmniboxMaxZPSMatchesName[] = "Omnibox Max ZPS Matches";
const char kOmniboxMaxZPSMatchesDescription[] =
    "Changes the maximum number of autocomplete matches displayed in the "
    "zero-prefix state in the omnibox (e.g. on NTP when tapped on OB).";

const char kOmniboxOnDeviceHeadSuggestionsIncognitoName[] =
    "Omnibox on device head suggestions (incognito only)";
const char kOmniboxOnDeviceHeadSuggestionsIncognitoDescription[] =
    "Shows Google head non personalized search suggestions provided by a "
    "compact on device model for incognito";

const char kOmniboxOnDeviceHeadSuggestionsNonIncognitoName[] =
    "Omnibox on device head suggestions (non-incognito only)";
const char kOmniboxOnDeviceHeadSuggestionsNonIncognitoDescription[] =
    "Shows Google head non personalized search suggestions provided by a "
    "compact on device model for non-incognito";

const char kOmniboxOnFocusSuggestionsName[] = "Omnibox on-focus suggestions";
const char kOmniboxOnFocusSuggestionsDescription[] =
    "Configures Omnibox on-focus suggestions - suggestions displayed on-focus "
    "before the user has typed any input. This provides overrides for the "
    "default suggestion locations.";

const char kOmniboxMaxURLMatchesName[] = "Omnibox Max URL matches";
const char kOmniboxMaxURLMatchesDescription[] =
    "Limit the number of URL suggestions in the omnibox. The omnibox will "
    "still display more than MaxURLMatches if there are no non-URL suggestions "
    "to replace them.";

const char kOmniboxNewImplementationName[] =
    "Use experimental omnibox textfield";
const char kOmniboxNewImplementationDescription[] =
    "Uses a textfield implementation that doesn't use UILabels internally";

const char kOmniboxFocusTriggersContextualWebZeroSuggestName[] =
    "Omnibox on-focus suggestions for the contextual Web";
const char kOmniboxFocusTriggersContextualWebZeroSuggestDescription[] =
    "Enables on-focus suggestions on the Open Web, that are contextual to the "
    "current URL. Will only work if user is signed-in and syncing, or is "
    "otherwise eligible to send the current page URL to the suggest server.";

const char kOmniboxFocusTriggersSRPZeroSuggestName[] =
    "Allow Omnibox contextual web on-focus suggestions on the SRP";
const char kOmniboxFocusTriggersSRPZeroSuggestDescription[] =
    "Enables on-focus suggestions on the Search Results page. Requires "
    "on-focus suggestions for the contextual web to be enabled. Will only work "
    "if user is signed-in and syncing.";

const char kOmniboxPasteButtonName[] = "Omnibox paste to search button";
const char kOmniboxPasteButtonDescription[] =
    "Add a paste button when showing clipboard suggestions in the omnibox. iOS "
    "16 and above.";

const char kOmniboxLocalHistoryZeroSuggestBeyondNTPName[] =
    "Allow local history zero-prefix suggestions beyond NTP";
const char kOmniboxLocalHistoryZeroSuggestBeyondNTPDescription[] =
    "Enables local history zero-prefix suggestions in every context in which "
    "the remote zero-prefix suggestions are enabled.";

const char kOmniboxZeroSuggestPrefetchingName[] =
    "Omnibox Zero Prefix Suggestion Prefetching on NTP";
const char kOmniboxZeroSuggestPrefetchingDescription[] =
    "Enables prefetching of the zero prefix suggestions for eligible users "
    "on the New Tab page.";

const char kOmniboxZeroSuggestInMemoryCachingName[] =
    "Omnibox Zero Prefix Suggestion in-memory caching";
const char kOmniboxZeroSuggestInMemoryCachingDescription[] =
    "Enables in-memory caching of zero prefix suggestions.";

const char kIOSOmniboxUpdatedPopupUIName[] = "Popup refresh";
const char kIOSOmniboxUpdatedPopupUIDescription[] =
    "Enable the new Popup implementation with Actions";

const char kOptimizationGuideDebugLogsName[] =
    "Enable optimization guide debug logs";
const char kOptimizationGuideDebugLogsDescription[] =
    "Enables the optimization guide to log and save debug messages that can be "
    "shown in the internals page.";

const char kOptimizationGuideModelDownloadingName[] =
    "Allow optimization guide model downloads";
const char kOptimizationGuideModelDownloadingDescription[] =
    "Enables the optimization guide to download prediction models.";

const char kOptimizationTargetPredictionDescription[] =
    "Enables prediction of optimization targets";
const char kOptimizationTargetPredictionName[] =
    "Enables usage of optimization guide TFLite models.";

const char kPasswordReuseDetectionName[] =
    "PhishGuard password reuse detection";
const char kPasswordReuseDetectionDescription[] =
    "Displays warning when user types or pastes a saved password into a "
    "phishing website.";

const char kRecordSnapshotSizeName[] =
    "Record the size of image and PDF snapshots in UMA histograms";
const char kRecordSnapshotSizeDescription[] =
    "When enabled, the app will record UMA histograms for image and PDF "
    "snapshots. PDF snaphot will be taken just for the purpose of the "
    "histogram recording.";

const char kRemoveExcessNTPsExperimentName[] = "Remove extra New Tab Pages";
const char kRemoveExcessNTPsExperimentDescription[] =
    "When enabled, extra tabs with the New Tab Page open and no navigation "
    "history will be removed.";

const char kRestoreSessionFromCacheName[] =
    "Use native WKWebView sesion restoration (iOS15 only).";
const char kRestoreSessionFromCacheDescription[] =
    "Enable instant session restoration for faster web session restoration "
    "(iOS15 only).";

const char kSafeBrowsingAvailableName[] = "Make Safe Browsing available";
const char kSafeBrowsingAvailableDescription[] =
    "When enabled, navigation URLs are compared to Safe Browsing blocklists, "
    "subject to an opt-out preference.";

const char kSafeBrowsingRealTimeLookupName[] = "Enable real-time Safe Browsing";
const char kSafeBrowsingRealTimeLookupDescription[] =
    "When enabled, navigation URLs are checked using real-time queries to Safe "
    "Browsing servers, subject to an opt-in preference.";

const char kSaveSessionTabsToSeparateFilesName[] =
    "Enable save tabs to separate files";
const char kSaveSessionTabsToSeparateFilesDescription[] =
    "When enabled, each Tab is saved in a separate file.";

const char kScreenTimeIntegrationName[] = "Enables ScreenTime Integration";
const char kScreenTimeIntegrationDescription[] =
    "Enables integration with ScreenTime in iOS 14.0 and above.";

const char kSendTabToSelfSigninPromoName[] = "Send tab to self sign-in promo";
const char kSendTabToSelfSigninPromoDescription[] =
    "Enables a sign-in promo if the user attempts to share a tab while being "
    "signed out";

const char kSendUmaOverAnyNetwork[] =
    "Send UMA data over any network available.";
const char kSendUmaOverAnyNetworkDescription[] =
    "When enabled, will send UMA data over either WiFi or cellular by default.";

const char kSharedHighlightingIOSName[] = "Enable Shared Highlighting features";
const char kSharedHighlightingIOSDescription[] =
    "Adds a Link to Text option in the Edit Menu which generates URLs with a "
    "text fragment.";

const char kShowAutofillTypePredictionsName[] = "Show Autofill predictions";
const char kShowAutofillTypePredictionsDescription[] =
    "Annotates web forms with Autofill field type predictions as placeholder "
    "text.";

const char kSmartSortingNewOverflowMenuName[] =
    "Smart Sorting the new Overflow Menu";
const char kSmartSortingNewOverflowMenuDescription[] =
    "Enables smart sorting the new overflow menu carousel by frecency.";

const char kNewOverflowMenuShareChromeActionName[] =
    "Share Chrome App action in the new overflow menu";
const char kNewOverflowMenuShareChromeActionDescription[] =
    "Enables the Share Chrome App action in the new overflow menu.";

const char kSyncSandboxName[] = "Use Chrome Sync sandbox";
const char kSyncSandboxDescription[] =
    "Connects to the testing server for Chrome Sync.";

const char kSynthesizedRestoreSessionName[] =
    "Use a synthesized native WKWebView sesion restoration (iOS15 only).";
const char kSynthesizedRestoreSessionDescription[] =
    "Enable instant session restoration by synthesizing WKWebView session "
    "restoration data (iOS15 only).";

const char kSyntheticCrashReportsForUteName[] =
    "Generate synthetic crash reports for UTE";
const char kSyntheticCrashReportsForUteDescription[] =
    "When enabled the app will create synthetic crash report when chrome "
    "starts up after Unexplained Termination Event (UTE).";

const char kSyncEnableHistoryDataTypeName[] = "Enable History sync data type";
const char kSyncEnableHistoryDataTypeDescription[] =
    "Enables the History sync data type instead of TypedURLs";

const char kSyncTrustedVaultPassphraseiOSRPCName[] =
    "Enable RPC for sync trusted vault passphrase.";
const char kSyncTrustedVaultPassphraseiOSRPCDescription[] =
    "Enables RPC for an experimental sync passphrase type, referred to as "
    "trusted vault.";

const char kSyncTrustedVaultPassphrasePromoName[] =
    "Enable promos for sync trusted vault passphrase.";
const char kSyncTrustedVaultPassphrasePromoDescription[] =
    "Enables promos for an experimental sync passphrase type, referred to as "
    "trusted vault.";

const char kSyncTrustedVaultPassphraseRecoveryName[] =
    "Enable sync trusted vault passphrase with improved recovery.";
const char kSyncTrustedVaultPassphraseRecoveryDescription[] =
    "Enables support for an experimental sync passphrase type, referred to as "
    "trusted vault, including logic and APIs for improved account recovery "
    "flows.";

const char kSyncInvalidationsName[] = "Use Sync standalone invalidations";
const char kSyncInvalidationsDescription[] =
    "If enabled, Sync will use standalone invalidations instead of topic based "
    "invalidations (Wallet and Offer data types are enabled by a dedicated "
    "flag).";

const char kSyncInvalidationsWalletAndOfferName[] =
    "Use Sync standalone invalidations for Wallet and Offer";
const char kSyncInvalidationsWalletAndOfferDescription[] =
    "If enabled, Sync will use standalone invalidations for Wallet and Offer "
    "data types. Takes effect only when Sync standalone invalidations are "
    "enabled.";

const char kStartSurfaceName[] = "Start Surface";
const char kStartSurfaceDescription[] =
    "Enable showing the Start Surface when launching Chrome via clicking the "
    "icon or the app switcher.";

const char kTFLiteLanguageDetectionName[] = "TFLite-based Language Detection";
const char kTFLiteLanguageDetectionDescription[] =
    "Uses TFLite for language detection in place of CLD3";

const char kTFLiteLanguageDetectionIgnoreName[] =
    "Ignore TFLite-based Language Detection";
const char kTFLiteLanguageDetectionIgnoreDescription[] =
    "Computes the TFLite language detection but ignore the result and uses the "
    "CLD3 detection instead.";

const char kToolbarContainerName[] = "Use Toolbar Containers";
const char kToolbarContainerDescription[] =
    "When enabled, the toolbars and their fullscreen animations will be "
    "managed by the toolbar container coordinator rather than BVC.";

extern const char kTrendingQueriesModuleName[] = "Show Trending Queries module";
extern const char kTrendingQueriesModuleDescription[] =
    "When enabled, the trending queries module will be shown in the NTP";

const char kUpdateHistoryEntryPointsInIncognitoName[] =
    "Update history entry points in Incognito.";
const char kUpdateHistoryEntryPointsInIncognitoDescription[] =
    "When enabled, the entry points to history UI from Incognito mode will be "
    "removed.";

const char kUseLensToSearchForImageName[] =
    "Use Google Lens to Search for images";
const char kUseLensToSearchForImageDescription[] =
    "When enabled, use Lens to search for images from the long press context "
    "menu when Google is the selected search engine.";

const char kEnableLensInHomeScreenWidgetName[] =
    "Enable Google Lens in the Home Screen Widget";
const char kEnableLensInHomeScreenWidgetDescription[] =
    "When enabled, use Lens to search for images from your device camera "
    "menu when Google is the selected search engine, accessible from the"
    "home screen widget.";

const char kEnableLensInKeyboardName[] =
    "Enable Google Lens in the Omnibox Keyboard";
const char kEnableLensInKeyboardDescription[] =
    "When enabled, use Lens to search for images from your device camera "
    "menu when Google is the selected search engine, accessible from the"
    "omnibox keyboard.";

const char kEnableLensInNTPName[] = "Enable Google Lens in the NTP";
const char kEnableLensInNTPDescription[] =
    "When enabled, use Lens to search for images from your device camera "
    "menu when Google is the selected search engine, accessible from the"
    "new tab page.";

const char kUseLoadSimulatedRequestForOfflinePageName[] =
    "Use loadSimulatedRequest:responseHTMLString: when displaying offline "
    "pages";
const char kUseLoadSimulatedRequestForOfflinePageDescription[] =
    "When enabled, the offline pages uses the iOS 15 "
    "loadSimulatedRequest:responseHTMLString: API";

const char kUseSFSymbolsName[] = "Replace Image by SFSymbols";
const char kUseSFSymbolsDescription[] =
    "When enabled, images are replaced by SFSymbols";

const char kWaitThresholdMillisecondsForCapabilitiesApiName[] =
    "Maximum wait time (in seconds) for a response from the Account "
    "Capabilities API";
const char kWaitThresholdMillisecondsForCapabilitiesApiDescription[] =
    "Used for testing purposes to test waiting thresholds in dev.";

const char kWalletServiceUseSandboxName[] = "Use Google Payments sandbox";
const char kWalletServiceUseSandboxDescription[] =
    "Uses the sandbox service for Google Payments API calls.";

const char kWebPageDefaultZoomFromDynamicTypeName[] =
    "Use dynamic type size for default text zoom level";
const char kWebPageDefaultZoomFromDynamicTypeDescription[] =
    "When enabled, the default text zoom level for a website comes from the "
    "current dynamic type setting.";

const char kWebPageAlternativeTextZoomName[] =
    "Use different method for zooming web pages";
const char kWebPageAlternativeTextZoomDescription[] =
    "When enabled, switches the method used to zoom web pages.";

const char kWebPageTextZoomIPadName[] = "Enable text zoom on iPad";
const char kWebPageTextZoomIPadDescription[] =
    "When enabled, text zoom works again on iPad";

const char kWhatsNewIOSName[] = "Enable What's New.";
const char kWhatsNewIOSDescription[] =
    "When enabled, What's New will display new features and chrome tips.";

// Please insert your name/description above in alphabetical order.

}  // namespace flag_descriptions
