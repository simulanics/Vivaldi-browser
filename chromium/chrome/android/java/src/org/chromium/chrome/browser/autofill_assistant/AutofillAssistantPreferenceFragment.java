// Copyright 2020 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.preferences.ChromePreferenceKeys;
import org.chromium.chrome.browser.preferences.Pref;
import org.chromium.chrome.browser.preferences.SharedPreferencesManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.settings.ChromeManagedPreferenceDelegate;
import org.chromium.chrome.browser.settings.SettingsLauncherImpl;
import org.chromium.chrome.browser.signin.services.UnifiedConsentServiceBridge;
import org.chromium.chrome.browser.sync.settings.GoogleServicesSettings;
import org.chromium.components.autofill_assistant.AssistantFeatures;
import org.chromium.components.browser_ui.settings.ChromeSwitchPreference;
import org.chromium.components.browser_ui.settings.ManagedPreferenceDelegate;
import org.chromium.components.browser_ui.settings.SettingsLauncher;
import org.chromium.components.browser_ui.settings.SettingsUtils;
import org.chromium.components.prefs.PrefService;
import org.chromium.components.user_prefs.UserPrefs;
import org.chromium.ui.text.NoUnderlineClickableSpan;
import org.chromium.ui.text.SpanApplier;

/**
 * Settings fragment for Autofill Assistant.
 */
public class AutofillAssistantPreferenceFragment
        extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {
    @VisibleForTesting
    public static final String PREF_WEB_ASSISTANCE_CATEGORY = "web_assistance";
    @VisibleForTesting
    public static final String PREF_AUTOFILL_ASSISTANT = "autofill_assistant_switch";
    @VisibleForTesting
    public static final String PREF_ASSISTANT_PROACTIVE_HELP_SWITCH = "proactive_help_switch";
    @VisibleForTesting
    public static final String PREF_GOOGLE_SERVICES_SETTINGS_LINK = "google_services_settings_link";
    @VisibleForTesting
    public static final String PREF_ASSISTANT_VOICE_SEARCH_CATEGORY = "voice_assistance";
    @VisibleForTesting
    public static final String PREF_ASSISTANT_VOICE_SEARCH_ENABLED_SWITCH =
            "voice_assistance_enabled";

    /** Chrome's {@link PrefService} that is used for Autofill Assistant settings. */
    private final PrefService mPrefService = UserPrefs.get(Profile.getLastUsedRegularProfile());
    /** SharedPreferences that are used for Assistant voice search settings. */
    private final SharedPreferencesManager mSharedPreferencesManager =
            SharedPreferencesManager.getInstance();

    private final ManagedPreferenceDelegate mManagedPreferenceDelegate =
            createManagedPreferenceDelegate();

    private PreferenceCategory mWebAssistanceCategory;
    private ChromeSwitchPreference mAutofillAssistantPreference;
    private ChromeSwitchPreference mProactiveHelpPreference;
    private ChromeSwitchPreference mAssistantVoiceSearchEnabledPref;
    private Preference mGoogleServicesSettingsLink;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        SettingsUtils.addPreferencesFromResource(this, R.xml.autofill_assistant_preferences);
        getActivity().setTitle(R.string.prefs_autofill_assistant_title);

        mWebAssistanceCategory = (PreferenceCategory) findPreference(PREF_WEB_ASSISTANCE_CATEGORY);
        if (!shouldShowWebAssistanceCategory()) {
            mWebAssistanceCategory.setVisible(false);
        }

        mAutofillAssistantPreference =
                (ChromeSwitchPreference) findPreference(PREF_AUTOFILL_ASSISTANT);
        if (shouldShowAutofillAssistantPreference()) {
            mAutofillAssistantPreference.setOnPreferenceChangeListener(this);
            mAutofillAssistantPreference.setManagedPreferenceDelegate(mManagedPreferenceDelegate);
        } else {
            mAutofillAssistantPreference.setVisible(false);
        }

        mProactiveHelpPreference =
                (ChromeSwitchPreference) findPreference(PREF_ASSISTANT_PROACTIVE_HELP_SWITCH);
        if (shouldShowAutofillAssistantProactiveHelpPreference()) {
            mProactiveHelpPreference.setOnPreferenceChangeListener(this);
            mAutofillAssistantPreference.setManagedPreferenceDelegate(mManagedPreferenceDelegate);
        } else {
            mProactiveHelpPreference.setVisible(false);
        }

        mGoogleServicesSettingsLink = findPreference(PREF_GOOGLE_SERVICES_SETTINGS_LINK);
        NoUnderlineClickableSpan linkSpan = new NoUnderlineClickableSpan(getContext(), view -> {
            SettingsLauncher settingsLauncher = new SettingsLauncherImpl();
            settingsLauncher.launchSettingsActivity(requireContext(), GoogleServicesSettings.class);
        });
        mGoogleServicesSettingsLink.setSummary(
                SpanApplier.applySpans(getString(R.string.prefs_proactive_help_sync_link),
                        new SpanApplier.SpanInfo("<link>", "</link>", linkSpan)));

        PreferenceCategory assistantVoiceSearchCategory =
                findPreference(PREF_ASSISTANT_VOICE_SEARCH_CATEGORY);
        mAssistantVoiceSearchEnabledPref =
                (ChromeSwitchPreference) findPreference(PREF_ASSISTANT_VOICE_SEARCH_ENABLED_SWITCH);
        if (!ChromeFeatureList.isEnabled(ChromeFeatureList.ASSISTANT_NON_PERSONALIZED_VOICE_SEARCH)
                && ChromeFeatureList.isEnabled(ChromeFeatureList.OMNIBOX_ASSISTANT_VOICE_SEARCH)) {
            mAssistantVoiceSearchEnabledPref.setOnPreferenceChangeListener(this);
        } else {
            assistantVoiceSearchCategory.setVisible(false);
            mAssistantVoiceSearchEnabledPref.setVisible(false);
        }

        updatePreferencesState();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case PREF_AUTOFILL_ASSISTANT:
                mPrefService.setBoolean(Pref.AUTOFILL_ASSISTANT_ENABLED, (boolean) newValue);
                updatePreferencesState();
                break;
            case PREF_ASSISTANT_PROACTIVE_HELP_SWITCH:
                mPrefService.setBoolean(
                        Pref.AUTOFILL_ASSISTANT_TRIGGER_SCRIPTS_ENABLED, (boolean) newValue);
                updatePreferencesState();
                break;
            case PREF_ASSISTANT_VOICE_SEARCH_ENABLED_SWITCH:
                SharedPreferencesManager.getInstance().writeBoolean(
                        ChromePreferenceKeys.ASSISTANT_VOICE_SEARCH_ENABLED, (boolean) newValue);
                break;
        }
        return true;
    }

    private ChromeManagedPreferenceDelegate createManagedPreferenceDelegate() {
        return preference -> {
            String key = preference.getKey();
            if (PREF_AUTOFILL_ASSISTANT.equals(key)) {
                return mPrefService.isManagedPreference(Pref.AUTOFILL_ASSISTANT_ENABLED);
            } else if (PREF_ASSISTANT_PROACTIVE_HELP_SWITCH.equals(key)) {
                return mPrefService.isManagedPreference(
                        Pref.AUTOFILL_ASSISTANT_TRIGGER_SCRIPTS_ENABLED);
            }
            return false;
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferencesState();
    }

    private boolean shouldShowAutofillAssistantPreference() {
        return AssistantFeatures.AUTOFILL_ASSISTANT.isEnabled()
                && !mPrefService.isDefaultValuePreference(Pref.AUTOFILL_ASSISTANT_ENABLED);
    }

    private boolean shouldShowAutofillAssistantProactiveHelpPreference() {
        return AssistantFeatures.AUTOFILL_ASSISTANT_PROACTIVE_HELP.isEnabled();
    }

    private boolean shouldShowWebAssistanceCategory() {
        return shouldShowAutofillAssistantProactiveHelpPreference()
                || shouldShowAutofillAssistantPreference();
    }

    private void updatePreferencesState() {
        boolean autofill_assistant_enabled =
                mPrefService.getBoolean(Pref.AUTOFILL_ASSISTANT_ENABLED);
        mAutofillAssistantPreference.setChecked(autofill_assistant_enabled);

        boolean assistant_switch_on_or_missing =
                !mAutofillAssistantPreference.isVisible() || autofill_assistant_enabled;
        boolean url_keyed_anonymized_data_collection_enabled =
                UnifiedConsentServiceBridge.isUrlKeyedAnonymizedDataCollectionEnabled(
                        Profile.getLastUsedRegularProfile());

        boolean proactive_help_on =
                mPrefService.getBoolean(Pref.AUTOFILL_ASSISTANT_TRIGGER_SCRIPTS_ENABLED);
        boolean proactive_toggle_enabled;
        boolean show_disclaimer;
        if (AssistantFeatures.AUTOFILL_ASSISTANT_DISABLE_PROACTIVE_HELP_TIED_TO_MSBB.isEnabled()) {
            proactive_toggle_enabled = assistant_switch_on_or_missing;
            show_disclaimer = false;
        } else {
            proactive_toggle_enabled =
                    url_keyed_anonymized_data_collection_enabled && assistant_switch_on_or_missing;
            show_disclaimer = !proactive_toggle_enabled && assistant_switch_on_or_missing;
        }
        mProactiveHelpPreference.setEnabled(proactive_toggle_enabled);
        mProactiveHelpPreference.setChecked(proactive_toggle_enabled && proactive_help_on);
        mGoogleServicesSettingsLink.setVisible(show_disclaimer);

        mAssistantVoiceSearchEnabledPref.setChecked(mSharedPreferencesManager.readBoolean(
                ChromePreferenceKeys.ASSISTANT_VOICE_SEARCH_ENABLED, /* default= */ false));
    }

    /** Open a page to learn more about the consent dialog. */
    public static void launchSettings(Context context) {
        SettingsLauncherImpl settingsLauncher = new SettingsLauncherImpl();
        settingsLauncher.launchSettingsActivity(
                context, AutofillAssistantPreferenceFragment.class, /* fragmentArgs= */ null);
    }
}
