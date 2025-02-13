// Copyright 2021 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.Activity;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.browser.customtabs.CustomTabsSessionToken;

import org.chromium.chrome.browser.customtabs.features.toolbar.CustomTabToolbar;
import org.chromium.chrome.browser.findinpage.FindToolbarObserver;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.lifecycle.ActivityLifecycleDispatcher;

/**
 * The default strategy for setting the height of the custom tab.
 */
public class CustomTabHeightStrategy implements FindToolbarObserver {
    public static CustomTabHeightStrategy createStrategy(Activity activity, @Px int initialHeight,
            Integer navigationBarColor, Integer navigationBarDividerColor,
            boolean isPartialCustomTabFixedHeight, CustomTabsConnection connection,
            @Nullable CustomTabsSessionToken session,
            ActivityLifecycleDispatcher lifecycleDispatcher, FullscreenManager fullscreenManager,
            boolean isTablet) {
        if (initialHeight <= 0) {
            return new CustomTabHeightStrategy();
        }

        return new PartialCustomTabHeightStrategy(activity, initialHeight, navigationBarColor,
                navigationBarDividerColor, isPartialCustomTabFixedHeight,
                size
                -> connection.onResized(session, size),
                lifecycleDispatcher, fullscreenManager, isTablet);
    }

    /**
     * @see {@link org.chromium.chrome.browser.lifecycle.InflationObserver#onPostInflationStartup()}
     */
    public void onPostInflationStartup() {}

    /**
     * Returns false if we didn't change the Window background color, true otherwise.
     */
    public boolean changeBackgroundColorForResizing() {
        return false;
    }

    /**
     * Provide this class with the required views and values so it can set up the strategy.
     *
     * @param coordinatorView Coordinator view to insert the UI handle for the users to resize the
     *                        custom tab.
     * @param toolbar The {@link CustomTabToolbar} to set up the strategy.
     * @param toolbarCornerRadius The custom tab corner radius in pixels.
     */
    public void onToolbarInitialized(
            View coordinatorView, CustomTabToolbar toolbar, @Px int toolbarCornerRadius) {}

    /**
     * @see {@link BaseCustomTabRootUiCoordinator#handleCloseAnimation()}
     */
    public void handleCloseAnimation(Runnable finishRunnable) {
        throw new IllegalStateException(
                "Custom close animation should be performed only on partial CCT.");
    }

    /**
     * Set the scrim value to apply to partial CCT UI.
     * @param scrimFraction Scrim fraction.
     */
    public void setScrimFraction(float scrimFraction) {}

    /**
     * {@see org.chromium.chrome.browser.ui.RootUiCoordinator#canDrawOutsideScreen()}
     */
    public boolean canDrawOutsideScreen() {
        return false;
    }

    // FindToolbarObserver implementation.

    @Override
    public void onFindToolbarShown() {}

    @Override
    public void onFindToolbarHidden() {}

    /**
     * Destroy the height strategy object.
     */
    public void destroy() {}
}
