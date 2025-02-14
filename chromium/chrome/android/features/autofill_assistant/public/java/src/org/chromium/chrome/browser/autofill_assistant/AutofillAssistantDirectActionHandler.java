// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import org.chromium.base.Callback;
import org.chromium.base.ThreadUtils;
import org.chromium.base.supplier.Supplier;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.browser_controls.BrowserControlsStateProvider;
import org.chromium.chrome.browser.directactions.DirectActionHandler;
import org.chromium.chrome.browser.directactions.DirectActionReporter;
import org.chromium.chrome.browser.directactions.DirectActionReporter.Definition;
import org.chromium.chrome.browser.directactions.DirectActionReporter.Type;
import org.chromium.chrome.browser.preferences.Pref;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.components.autofill_assistant.AutofillAssistantActionHandler;
import org.chromium.components.autofill_assistant.AutofillAssistantDirectAction;
import org.chromium.components.autofill_assistant.AutofillAssistantModuleEntry;
import org.chromium.components.autofill_assistant.AutofillAssistantModuleEntryProvider;
import org.chromium.components.browser_ui.bottomsheet.BottomSheetController;
import org.chromium.components.prefs.PrefService;
import org.chromium.components.user_prefs.UserPrefs;
import org.chromium.content_public.browser.WebContents;

/**
 * A handler that provides just enough functionality to allow on-demand loading of the module
 * through direct actions. The actual implementation is in the module.
 */
public class AutofillAssistantDirectActionHandler implements DirectActionHandler {
    private static final String FETCH_WEBSITE_ACTIONS = "fetch_website_actions";
    private static final String FETCH_WEBSITE_ACTIONS_RESULT = "success";
    private static final String AA_ACTION_RESULT = "success";
    private static final String ACTION_NAME = "name";
    private static final String EXPERIMENT_IDS = "experiment_ids";
    private static final String ONBOARDING_ACTION = "onboarding";
    private static final String ONBOARDING_AND_START_ACTION = "onboarding_and_start";
    private static final String USER_NAME = "user_name";
    private static final String SHOW_ERROR_ON_FAILURE = "show_error_on_failure";

    private final Context mContext;
    private final BottomSheetController mBottomSheetController;
    private final BrowserControlsStateProvider mBrowserControls;
    private final View mRootView;
    private final ActivityTabProvider mActivityTabProvider;
    private final Supplier<WebContents> mWebContentsSupplier;
    private final AutofillAssistantModuleEntryProvider mModuleEntryProvider;

    @Nullable
    private AutofillAssistantActionHandler mDelegate;

    AutofillAssistantDirectActionHandler(Context context,
            BottomSheetController bottomSheetController,
            BrowserControlsStateProvider browserControls, View rootView,
            ActivityTabProvider activityTabProvider, Supplier<WebContents> webContentsSupplier,
            AutofillAssistantModuleEntryProvider moduleEntryProvider) {
        mContext = context;
        mBottomSheetController = bottomSheetController;
        mBrowserControls = browserControls;
        mRootView = rootView;
        mActivityTabProvider = activityTabProvider;
        mWebContentsSupplier = webContentsSupplier;
        mModuleEntryProvider = moduleEntryProvider;
    }

    private PrefService getPrefs() {
        return UserPrefs.get(Profile.getLastUsedRegularProfile());
    }

    @Override
    public void reportAvailableDirectActions(DirectActionReporter reporter) {
        ThreadUtils.assertOnUiThread();

        if (!getPrefs().getBoolean(Pref.AUTOFILL_ASSISTANT_ENABLED)) {
            return;
        }

        if (!getPrefs().getBoolean(Pref.AUTOFILL_ASSISTANT_CONSENT)) {
            reporter.addDirectAction(ONBOARDING_ACTION)
                    .withParameter(ACTION_NAME, Type.STRING, /* required= */ false)
                    .withParameter(EXPERIMENT_IDS, Type.STRING, /* required= */ false)
                    .withResult(AA_ACTION_RESULT, Type.BOOLEAN);
            reporter.addDirectAction(ONBOARDING_AND_START_ACTION)
                    .withParameter(ACTION_NAME, Type.STRING, /* required= */ true)
                    .withParameter(USER_NAME, Type.STRING, /* required= */ false)
                    .withParameter(EXPERIMENT_IDS, Type.STRING, /* required= */ false)
                    .withParameter(SHOW_ERROR_ON_FAILURE, Type.BOOLEAN, /* required= */ false)
                    .withResult(AA_ACTION_RESULT, Type.BOOLEAN);
            return;
        }

        if (mDelegate == null || (mDelegate != null && !mDelegate.hasRunFirstCheck())) {
            reporter.addDirectAction(FETCH_WEBSITE_ACTIONS)
                    .withParameter(USER_NAME, Type.STRING, /* required= */ false)
                    .withParameter(EXPERIMENT_IDS, Type.STRING, /* required= */ false)
                    .withResult(FETCH_WEBSITE_ACTIONS_RESULT, Type.BOOLEAN);
        } else {
            // Otherwise we are already done fetching scripts and can just return the ones we know
            // about.
            for (AutofillAssistantDirectAction action : mDelegate.getActions()) {
                for (String name : action.getNames()) {
                    Definition definition = reporter.addDirectAction(name)
                                                    .withParameter(EXPERIMENT_IDS, Type.STRING,
                                                            /* required= */ false)
                                                    .withResult(AA_ACTION_RESULT, Type.BOOLEAN);

                    // TODO(b/138833619): Support non-string arguments. Requires updating the proto
                    // definition.
                    for (String required : action.getRequiredArguments()) {
                        definition.withParameter(required, Type.STRING, /* required= */ true);
                    }
                    for (String optional : action.getOptionalArguments()) {
                        definition.withParameter(optional, Type.STRING, /* required= */ false);
                    }
                }
            }
        }
    }

    @Override
    public boolean performDirectAction(
            String actionId, Bundle arguments, Callback<Bundle> callback) {
        if (actionId.equals(FETCH_WEBSITE_ACTIONS)
                && getPrefs().getBoolean(Pref.AUTOFILL_ASSISTANT_CONSENT)) {
            fetchWebsiteActions(arguments, callback);
            return true;
        }
        // Only handle and perform the action if it is known to the controller.
        if (isActionAvailable(actionId) || ONBOARDING_ACTION.equals(actionId)
                || ONBOARDING_AND_START_ACTION.equals(actionId)) {
            performAction(actionId, arguments, callback);
            return true;
        }
        return false;
    }

    private boolean isActionAvailable(String actionId) {
        if (mDelegate == null || !mDelegate.hasRunFirstCheck()) return false;
        for (AutofillAssistantDirectAction action : mDelegate.getActions()) {
            if (action.getNames().contains(actionId)) return true;
        }
        return false;
    }

    private void fetchWebsiteActions(Bundle arguments, Callback<Bundle> bundleCallback) {
        Callback<Boolean> successCallback = (success) -> {
            Bundle bundle = new Bundle();
            bundle.putBoolean(FETCH_WEBSITE_ACTIONS_RESULT, success);
            bundleCallback.onResult(bundle);
        };

        if (!getPrefs().getBoolean(Pref.AUTOFILL_ASSISTANT_ENABLED)
                || !getPrefs().getBoolean(Pref.AUTOFILL_ASSISTANT_CONSENT)) {
            successCallback.onResult(false);
            return;
        }

        String userName = arguments.getString(USER_NAME, "");
        arguments.remove(USER_NAME);

        String experimentIds = arguments.getString(EXPERIMENT_IDS, "");
        arguments.remove(EXPERIMENT_IDS);

        getDelegate(/* installIfNecessary= */ false, (delegate) -> {
            if (delegate == null) {
                successCallback.onResult(false);
                return;
            }
            delegate.fetchWebsiteActions(userName, experimentIds, arguments, successCallback);
        });
    }

    private void performAction(String actionId, Bundle arguments, Callback<Bundle> bundleCallback) {
        Callback<Boolean> booleanCallback = (result) -> {
            Bundle bundle = new Bundle();
            bundle.putBoolean(AA_ACTION_RESULT, result);
            bundleCallback.onResult(bundle);
        };

        if (!getPrefs().getBoolean(Pref.AUTOFILL_ASSISTANT_ENABLED)) {
            booleanCallback.onResult(false);
            return;
        }

        String experimentIds = arguments.getString(EXPERIMENT_IDS, "");
        arguments.remove(EXPERIMENT_IDS);

        getDelegate(/* installIfNecessary= */ true, (delegate) -> {
            if (delegate == null) {
                booleanCallback.onResult(false);
                return;
            }
            if (delegate.isSupervisedUser()) {
                booleanCallback.onResult(false);
                return;
            }
            if (ONBOARDING_ACTION.equals(actionId)) {
                delegate.performOnboarding(experimentIds, arguments, booleanCallback);
                return;
            }
            if (ONBOARDING_AND_START_ACTION.equals(actionId)) {
                delegate.performOnboarding(experimentIds, arguments, onboardingResult -> {
                    if (!onboardingResult) {
                        booleanCallback.onResult(false);
                        return;
                    }
                    boolean showErrorOnFailure = arguments.getBoolean(SHOW_ERROR_ON_FAILURE, false);
                    delegate.fetchWebsiteActions(arguments.getString(USER_NAME, ""),
                            arguments.getString(EXPERIMENT_IDS, ""), arguments,
                            fetchActionsResult -> {
                                if (!fetchActionsResult) {
                                    booleanCallback.onResult(false);
                                    if (showErrorOnFailure) {
                                        delegate.showFatalError();
                                    }
                                    return;
                                }
                                String afterOnboardingActionId =
                                        arguments.getString(ACTION_NAME, "");
                                if (!isActionAvailable(afterOnboardingActionId)) {
                                    booleanCallback.onResult(false);
                                    if (showErrorOnFailure) {
                                        delegate.showFatalError();
                                    }
                                    return;
                                }
                                delegate.performAction(afterOnboardingActionId, experimentIds,
                                        arguments, booleanCallback);
                            });
                });
                return;
            }

            Callback<Boolean> successCallback = (success) -> {
                booleanCallback.onResult(success && !delegate.getActions().isEmpty());
            };
            delegate.performAction(actionId, experimentIds, arguments, successCallback);
        });
    }

    /**
     * Builds the delegate, if possible, and pass it to the callback.
     *
     * <p>If necessary, this function creates a delegate instance and keeps it in {@link
     * #mDelegate}.
     *
     * @param installIfNecessary if true, install the DFM if necessary
     * @param callback callback to report the delegate to
     */
    private void getDelegate(
            boolean installIfNecessary, Callback<AutofillAssistantActionHandler> callback) {
        if (mDelegate == null) {
            mDelegate = createDelegate(mModuleEntryProvider.getModuleEntryIfInstalled());
        }
        if (mDelegate != null || !installIfNecessary) {
            callback.onResult(mDelegate);
            return;
        }

        Tab tab = mActivityTabProvider.get();
        if (tab == null) {
            // TODO(b/134741524): Allow DFM loading UI to work with no tabs.
            callback.onResult(null);
            return;
        }
        mModuleEntryProvider.getModuleEntry((entry) -> {
            mDelegate = createDelegate(entry);
            callback.onResult(mDelegate);
        }, new AssistantModuleInstallUiProviderChrome(tab), /* showUi = */ true);
    }

    /** Creates a delegate from the given {@link AutofillAssistantModuleEntry}, if possible. */
    @Nullable
    private AutofillAssistantActionHandler createDelegate(
            @Nullable AutofillAssistantModuleEntry entry) {
        if (entry == null) return null;

        return entry.createActionHandler(mContext, mBottomSheetController,
                ()
                        -> new AssistantBrowserControlsChrome(mBrowserControls),
                mRootView, mWebContentsSupplier, new AssistantStaticDependenciesChrome());
    }
}
