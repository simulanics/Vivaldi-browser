// Copyright 2020 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync.settings;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.commerce.ShoppingFeatures;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchFieldTrial;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManager;
import org.chromium.chrome.browser.feedback.HelpAndFeedbackLauncherImpl;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.metrics.UmaSessionStats;
import org.chromium.chrome.browser.preferences.Pref;
import org.chromium.chrome.browser.price_tracking.PriceTrackingFeatures;
import org.chromium.chrome.browser.price_tracking.PriceTrackingUtilities;
import org.chromium.chrome.browser.privacy.settings.PrivacyPreferencesManagerImpl;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.settings.ChromeManagedPreferenceDelegate;
import org.chromium.chrome.browser.signin.services.IdentityServicesProvider;
import org.chromium.chrome.browser.signin.services.SigninManager;
import org.chromium.chrome.browser.signin.services.UnifiedConsentServiceBridge;
import org.chromium.chrome.browser.ui.signin.SignOutDialogCoordinator;
import org.chromium.chrome.browser.ui.signin.SignOutDialogCoordinator.Listener;
import org.chromium.components.autofill_assistant.AssistantFeatures;
import org.chromium.components.browser_ui.settings.ChromeSwitchPreference;
import org.chromium.components.browser_ui.settings.ManagedPreferenceDelegate;
import org.chromium.components.browser_ui.settings.SettingsUtils;
import org.chromium.components.prefs.PrefService;
import org.chromium.components.signin.GAIAServiceType;
import org.chromium.components.signin.identitymanager.ConsentLevel;
import org.chromium.components.signin.identitymanager.IdentityManager;
import org.chromium.components.signin.metrics.SignoutReason;
import org.chromium.components.user_prefs.UserPrefs;
import org.chromium.ui.modaldialog.ModalDialogManagerHolder;

/**
 * Settings fragment controlling a number of features communicating with Google services, such as
 * search autocomplete and the automatic upload of crash reports.
 */
public class GoogleServicesSettings extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener, Listener {
    private static final String SIGN_OUT_DIALOG_TAG = "sign_out_dialog_tag";
    private static final String CLEAR_DATA_PROGRESS_DIALOG_TAG = "clear_data_progress";

    @VisibleForTesting
    public static final String PREF_ALLOW_SIGNIN = "allow_signin";
    private static final String PREF_SEARCH_SUGGESTIONS = "search_suggestions";
    private static final String PREF_USAGE_AND_CRASH_REPORTING = "usage_and_crash_reports";
    private static final String PREF_URL_KEYED_ANONYMIZED_DATA = "url_keyed_anonymized_data";
    private static final String PREF_CONTEXTUAL_SEARCH = "contextual_search";
    @VisibleForTesting
    public static final String PREF_AUTOFILL_ASSISTANT = "autofill_assistant";
    @VisibleForTesting
    public static final String PREF_AUTOFILL_ASSISTANT_SUBSECTION = "autofill_assistant_subsection";
    @VisibleForTesting
    public static final String PREF_METRICS_SETTINGS = "metrics_settings";
    @VisibleForTesting
    public static final String PREF_PRICE_TRACKING_ANNOTATIONS = "price_tracking_annotations";
    private static final String PREF_PRICE_NOTIFICATION_SECTION = "price_notifications_section";

    private final PrefService mPrefService = UserPrefs.get(Profile.getLastUsedRegularProfile());
    private final PrivacyPreferencesManagerImpl mPrivacyPrefManager =
            PrivacyPreferencesManagerImpl.getInstance();
    private final ManagedPreferenceDelegate mManagedPreferenceDelegate =
            createManagedPreferenceDelegate();

    private ChromeSwitchPreference mAllowSignin;
    private ChromeSwitchPreference mSearchSuggestions;
    private ChromeSwitchPreference mUsageAndCrashReporting;
    private ChromeSwitchPreference mUrlKeyedAnonymizedData;
    private ChromeSwitchPreference mPriceTrackingAnnotations;
    private @Nullable ChromeSwitchPreference mAutofillAssistant;
    private @Nullable Preference mContextualSearch;
    private Preference mPriceNotificationSection;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        getActivity().setTitle(R.string.prefs_google_services);
        setHasOptionsMenu(true);

        SettingsUtils.addPreferencesFromResource(this, R.xml.google_services_preferences);

        mAllowSignin = (ChromeSwitchPreference) findPreference(PREF_ALLOW_SIGNIN);

        if (Profile.getLastUsedRegularProfile().isChild()) {
            // Do not display option to allow / disallow sign-in for supervised accounts since
            // these require the user to be signed-in and syncing.
            mAllowSignin.setVisible(false);
        } else {
            mAllowSignin.setOnPreferenceChangeListener(this);
            mAllowSignin.setManagedPreferenceDelegate(mManagedPreferenceDelegate);
        }

        mSearchSuggestions = (ChromeSwitchPreference) findPreference(PREF_SEARCH_SUGGESTIONS);
        mSearchSuggestions.setOnPreferenceChangeListener(this);
        mSearchSuggestions.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        // If the metrics-settings-android flag is not enabled, remove the corresponding element.
        if (!ChromeFeatureList.isEnabled(ChromeFeatureList.METRICS_SETTINGS_ANDROID)) {
            removePreference(getPreferenceScreen(), findPreference(PREF_METRICS_SETTINGS));
        }

        mUsageAndCrashReporting =
                (ChromeSwitchPreference) findPreference(PREF_USAGE_AND_CRASH_REPORTING);
        mUsageAndCrashReporting.setOnPreferenceChangeListener(this);
        mUsageAndCrashReporting.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        mUrlKeyedAnonymizedData =
                (ChromeSwitchPreference) findPreference(PREF_URL_KEYED_ANONYMIZED_DATA);
        mUrlKeyedAnonymizedData.setOnPreferenceChangeListener(this);
        mUrlKeyedAnonymizedData.setManagedPreferenceDelegate(mManagedPreferenceDelegate);

        mAutofillAssistant = (ChromeSwitchPreference) findPreference(PREF_AUTOFILL_ASSISTANT);
        Preference autofillAssistantSubsection = findPreference(PREF_AUTOFILL_ASSISTANT_SUBSECTION);
        // Assistant autofill/voicesearch both live in the sub-section. If either one of them is
        // enabled, then the subsection should show.
        if (AssistantFeatures.AUTOFILL_ASSISTANT_PROACTIVE_HELP.isEnabled()
                || shouldShowAssistantVoiceSearchSetting()) {
            removePreference(getPreferenceScreen(), mAutofillAssistant);
            mAutofillAssistant = null;
            autofillAssistantSubsection.setVisible(true);
        } else if (shouldShowAutofillAssistantPreference()) {
            mAutofillAssistant.setOnPreferenceChangeListener(this);
            mAutofillAssistant.setManagedPreferenceDelegate(mManagedPreferenceDelegate);
        } else {
            removePreference(getPreferenceScreen(), mAutofillAssistant);
            mAutofillAssistant = null;
        }

        mContextualSearch = findPreference(PREF_CONTEXTUAL_SEARCH);
        if (!ContextualSearchFieldTrial.isEnabled()) {
            removePreference(getPreferenceScreen(), mContextualSearch);
            mContextualSearch = null;
        }

        mPriceTrackingAnnotations =
                (ChromeSwitchPreference) findPreference(PREF_PRICE_TRACKING_ANNOTATIONS);
        if (!PriceTrackingFeatures.allowUsersToDisablePriceAnnotations()) {
            removePreference(getPreferenceScreen(), mPriceTrackingAnnotations);
            mPriceTrackingAnnotations = null;
        } else {
            mPriceTrackingAnnotations.setOnPreferenceChangeListener(this);
            mPriceTrackingAnnotations.setManagedPreferenceDelegate(mManagedPreferenceDelegate);
        }

        mPriceNotificationSection = findPreference(PREF_PRICE_NOTIFICATION_SECTION);
        if (ShoppingFeatures.isShoppingListEnabled()) {
            mPriceNotificationSection.setVisible(true);
        } else {
            removePreference(getPreferenceScreen(), mPriceNotificationSection);
            mPriceNotificationSection = null;
        }

        updatePreferences();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        MenuItem help =
                menu.add(Menu.NONE, R.id.menu_id_targeted_help, Menu.NONE, R.string.menu_help);
        help.setIcon(R.drawable.ic_help_and_feedback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_id_targeted_help) {
            HelpAndFeedbackLauncherImpl.getInstance().show(getActivity(),
                    getString(R.string.help_context_sync_and_services),
                    Profile.getLastUsedRegularProfile(), null);
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (PREF_ALLOW_SIGNIN.equals(key)) {
            assert !Profile.getLastUsedRegularProfile().isChild()
                : "A supervised account must not update allow sign-in.";

            IdentityManager identityManager = IdentityServicesProvider.get().getIdentityManager(
                    Profile.getLastUsedRegularProfile());
            boolean shouldSignUserOut =
                    identityManager.hasPrimaryAccount(ConsentLevel.SIGNIN) && !((boolean) newValue);
            if (!shouldSignUserOut) {
                mPrefService.setBoolean(Pref.SIGNIN_ALLOWED, (boolean) newValue);
                return true;
            }

            boolean shouldShowSignOutDialog =
                    identityManager.getPrimaryAccountInfo(ConsentLevel.SYNC) != null;
            if (!shouldShowSignOutDialog) {
                // Don't show signout dialog if there's no sync consent, as it never wipes the data.
                IdentityServicesProvider.get()
                        .getSigninManager(Profile.getLastUsedRegularProfile())
                        .signOut(SignoutReason.USER_CLICKED_SIGNOUT_SETTINGS, null, false);
                mPrefService.setBoolean(Pref.SIGNIN_ALLOWED, false);
                return true;
            }

            SignOutDialogCoordinator.show(requireContext(),
                    ((ModalDialogManagerHolder) getActivity()).getModalDialogManager(), this,
                    SignOutDialogCoordinator.ActionType.CLEAR_PRIMARY_ACCOUNT,
                    GAIAServiceType.GAIA_SERVICE_TYPE_NONE);
            // Don't change the preference state yet, it will be updated by onSignOutClicked
            // if the user actually confirms the sign-out.
            return false;
        } else if (PREF_SEARCH_SUGGESTIONS.equals(key)) {
            mPrefService.setBoolean(Pref.SEARCH_SUGGEST_ENABLED, (boolean) newValue);
        } else if (PREF_USAGE_AND_CRASH_REPORTING.equals(key)) {
            UmaSessionStats.changeMetricsReportingConsent((boolean) newValue);
        } else if (PREF_URL_KEYED_ANONYMIZED_DATA.equals(key)) {
            UnifiedConsentServiceBridge.setUrlKeyedAnonymizedDataCollectionEnabled(
                    Profile.getLastUsedRegularProfile(), (boolean) newValue);
        } else if (PREF_AUTOFILL_ASSISTANT.equals(key)) {
            mPrefService.setBoolean(Pref.AUTOFILL_ASSISTANT_ENABLED, (boolean) newValue);
        } else if (PREF_PRICE_TRACKING_ANNOTATIONS.equals(key)) {
            PriceTrackingUtilities.setTrackPricesOnTabsEnabled((boolean) newValue);
        }
        return true;
    }

    private static void removePreference(PreferenceGroup from, Preference preference) {
        boolean found = from.removePreference(preference);
        assert found : "Don't have such preference! Preference key: " + preference.getKey();
    }

    private void updatePreferences() {
        mAllowSignin.setChecked(mPrefService.getBoolean(Pref.SIGNIN_ALLOWED));
        mSearchSuggestions.setChecked(mPrefService.getBoolean(Pref.SEARCH_SUGGEST_ENABLED));
        mUsageAndCrashReporting.setChecked(mPrivacyPrefManager.isUsageAndCrashReportingPermitted());
        mUrlKeyedAnonymizedData.setChecked(
                UnifiedConsentServiceBridge.isUrlKeyedAnonymizedDataCollectionEnabled(
                        Profile.getLastUsedRegularProfile()));

        if (mAutofillAssistant != null) {
            mAutofillAssistant.setChecked(mPrefService.getBoolean(Pref.AUTOFILL_ASSISTANT_ENABLED));
        }
        if (mContextualSearch != null) {
            boolean isContextualSearchEnabled =
                    !ContextualSearchManager.isContextualSearchDisabled();
            mContextualSearch.setSummary(
                    isContextualSearchEnabled ? R.string.text_on : R.string.text_off);
        }
        if (mPriceTrackingAnnotations != null) {
            mPriceTrackingAnnotations.setChecked(
                    PriceTrackingUtilities.isTrackPricesOnTabsEnabled());
        }
    }

    private ChromeManagedPreferenceDelegate createManagedPreferenceDelegate() {
        return preference -> {
            String key = preference.getKey();
            if (PREF_ALLOW_SIGNIN.equals(key)) {
                return mPrefService.isManagedPreference(Pref.SIGNIN_ALLOWED);
            }
            if (PREF_SEARCH_SUGGESTIONS.equals(key)) {
                return mPrefService.isManagedPreference(Pref.SEARCH_SUGGEST_ENABLED);
            }
            if (PREF_USAGE_AND_CRASH_REPORTING.equals(key)) {
                return !PrivacyPreferencesManagerImpl.getInstance()
                                .isUsageAndCrashReportingPermittedByPolicy();
            }
            if (PREF_URL_KEYED_ANONYMIZED_DATA.equals(key)) {
                return UnifiedConsentServiceBridge.isUrlKeyedAnonymizedDataCollectionManaged(
                        Profile.getLastUsedRegularProfile());
            }
            return false;
        };
    }

    /**
     *  This checks whether Autofill Assistant is enabled and was shown at least once (only then
     *  will the Autofill Assistant switch be assigned a value).
     */
    private boolean shouldShowAutofillAssistantPreference() {
        return AssistantFeatures.AUTOFILL_ASSISTANT.isEnabled()
                && !mPrefService.isDefaultValuePreference(Pref.AUTOFILL_ASSISTANT_ENABLED);
    }

    /**
     * Whether or not the Assistant voice search section with a toggle should be shown.
     */
    public boolean shouldShowAssistantVoiceSearchSetting() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.OMNIBOX_ASSISTANT_VOICE_SEARCH)
                && !ChromeFeatureList.isEnabled(
                        ChromeFeatureList.ASSISTANT_NON_PERSONALIZED_VOICE_SEARCH);
    }

    // SignOutDialogListener implementation:
    @Override
    public void onSignOutClicked(boolean forceWipeUserData) {
        // In case the user reached this fragment without being signed in, we guard the sign out so
        // we do not hit a native crash.
        if (!IdentityServicesProvider.get()
                        .getIdentityManager(Profile.getLastUsedRegularProfile())
                        .hasPrimaryAccount(ConsentLevel.SIGNIN)) {
            return;
        }
        final DialogFragment clearDataProgressDialog = new ClearDataProgressDialog();
        IdentityServicesProvider.get()
                .getSigninManager(Profile.getLastUsedRegularProfile())
                .signOut(SignoutReason.USER_CLICKED_SIGNOUT_SETTINGS,
                        new SigninManager.SignOutCallback() {
                            @Override
                            public void preWipeData() {
                                clearDataProgressDialog.show(
                                        getFragmentManager(), CLEAR_DATA_PROGRESS_DIALOG_TAG);
                            }

                            @Override
                            public void signOutComplete() {
                                clearDataProgressDialog.dismissAllowingStateLoss();
                            }
                        },
                        forceWipeUserData);
        mPrefService.setBoolean(Pref.SIGNIN_ALLOWED, false);
        updatePreferences();
    }
}
