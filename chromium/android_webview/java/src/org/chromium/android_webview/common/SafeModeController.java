// Copyright 2021 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview.common;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.StrictModeContext;
import org.chromium.build.BuildConfig;

import java.util.HashSet;
import java.util.Set;

/**
 * A browser-process class for querying SafeMode state and executing SafeModeActions.
 */
public class SafeModeController {
    public static final String SAFE_MODE_STATE_COMPONENT =
            "org.chromium.android_webview.SafeModeState";
    public static final String URI_AUTHORITY_SUFFIX = ".SafeModeContentProvider";
    public static final String SAFE_MODE_ACTIONS_URI_PATH = "/safe-mode-actions";
    public static final String ACTIONS_COLUMN = "actions";

    private static final String TAG = "WebViewSafeMode";

    private SafeModeAction[] mRegisteredActions;

    private SafeModeController() {}

    private static SafeModeController sInstanceForTests;

    private static class LazyHolder {
        static final SafeModeController INSTANCE = new SafeModeController();
    }

    /**
     * Sets the singleton instance for testing. Not thread safe, must only be called from single
     * threaded tests.
     * @param controller The SafeModeController object to return from getInstance(). Passing in a
     * null value resets this.
     */
    @VisibleForTesting
    public static void setInstanceForTests(SafeModeController controller) {
        sInstanceForTests = controller;
    }

    public static SafeModeController getInstance() {
        return sInstanceForTests == null ? LazyHolder.INSTANCE : sInstanceForTests;
    }

    /**
     * Registers a list of {@link SafeModeAction}s which can be executed. This must only be called
     * once (per-process) and each action in the list must have a unique ID.
     *
     * @throws IllegalStateException if actions have already been registered.
     * @throws IllegalArgumentException if there are any duplicates.
     */
    public void registerActions(@NonNull SafeModeAction[] actions) {
        if (mRegisteredActions != null) {
            throw new IllegalStateException("Already registered a list of actions in this process");
        }
        if (BuildConfig.ENABLE_ASSERTS) {
            // Verify we don't register any duplicate IDs. Only check this in debug builds to avoid
            // delaying startup.
            Set<String> allIds = new HashSet<>();
            for (SafeModeAction action : actions) {
                if (!allIds.add(action.getId())) {
                    throw new IllegalArgumentException("Received duplicate ID: " + action.getId());
                }
            }
        }
        mRegisteredActions = actions;
    }

    @VisibleForTesting
    public void unregisterActionsForTesting() {
        mRegisteredActions = null;
    }

    /**
     * Queries SafeModeContentProvider for the set of actions which should be applied. Returns the
     * empty set if SafeMode is disabled. This should only be called from embedded WebView contexts.
     */
    public Set<String> queryActions(String webViewPackageName) {
        Set<String> actions = new HashSet<>();

        Uri uri = new Uri.Builder()
                          .scheme("content")
                          .authority(webViewPackageName + URI_AUTHORITY_SUFFIX)
                          .path(SAFE_MODE_ACTIONS_URI_PATH)
                          .build();

        final Context appContext = ContextUtils.getApplicationContext();
        try (Cursor cursor = appContext.getContentResolver().query(uri, /* projection */ null,
                     /* selection */ null, /* selectionArgs */ null, /* sortOrder */ null)) {
            assert cursor != null : "ContentProvider doesn't support querying '" + uri + "'";
            int actionIdColumnIndex = cursor.getColumnIndexOrThrow(ACTIONS_COLUMN);
            while (cursor.moveToNext()) {
                actions.add(cursor.getString(actionIdColumnIndex));
            }
        }

        Log.i(TAG, "Received SafeModeActions: %s", actions);
        return actions;
    }

    /**
     * Executes the given set of {@link SafeModeAction}s. Execution order is determined by the order
     * of the array registered by {@link registerActions}.
     *
     * @return {@code true} if <b>all</b> actions succeeded, {@code false} otherwise.
     * @throws IllegalStateException if this is called before {@link registerActions}.
     */
    public boolean executeActions(Set<String> actionsToExecute) {
        // Execute SafeModeActions in a deterministic order.
        if (mRegisteredActions == null) {
            throw new IllegalStateException(
                    "Must registerActions() before calling executeActions()");
        }
        boolean overallStatus = true;
        for (SafeModeAction action : mRegisteredActions) {
            if (actionsToExecute.contains(action.getId())) {
                // Allow SafeModeActions in general to perform disk reads and writes.
                try (StrictModeContext ignored = StrictModeContext.allowDiskWrites()) {
                    Log.i(TAG, "Starting to execute %s", action.getId());
                    boolean status = action.execute();
                    overallStatus &= status;
                    if (status) {
                        Log.i(TAG, "Finished executing %s (%s)", action.getId(), "success");
                    } else {
                        Log.e(TAG, "Finished executing %s (%s)", action.getId(), "failure");
                    }
                }
            }
        }
        return overallStatus;
    }

    /**
     * Quickly determine whether SafeMode is enabled. SafeMode is off-by-default.
     *
     * @param webViewPackageName the package name of the WebView implementation to query about
     *     SafeMode (generally this is the current WebView provider).
     */
    public boolean isSafeModeEnabled(String webViewPackageName) {
        final Context context = ContextUtils.getApplicationContext();
        ComponentName safeModeComponent =
                new ComponentName(webViewPackageName, SAFE_MODE_STATE_COMPONENT);
        int enabledState =
                context.getPackageManager().getComponentEnabledSetting(safeModeComponent);
        return enabledState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
}
