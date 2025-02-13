// Copyright 2020 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.features.start_surface;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.chromium.chrome.browser.tasks.ReturnToChromeUtil.TAB_SWITCHER_ON_RETURN_MS;
import static org.chromium.ui.test.util.ViewUtils.onViewWaiting;

import androidx.test.espresso.action.GeneralClickAction;
import androidx.test.espresso.action.GeneralLocation;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Tap;
import androidx.test.espresso.action.ViewActions;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.chromium.base.test.params.ParameterAnnotations;
import org.chromium.base.test.params.ParameterAnnotations.UseRunnerDelegate;
import org.chromium.base.test.params.ParameterSet;
import org.chromium.base.test.params.ParameterizedRunner;
import org.chromium.base.test.util.CommandLineFlags;
import org.chromium.base.test.util.CriteriaHelper;
import org.chromium.base.test.util.DisabledTest;
import org.chromium.base.test.util.DoNotBatch;
import org.chromium.base.test.util.Feature;
import org.chromium.base.test.util.Restriction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.flags.CachedFeatureFlags;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.flags.ChromeSwitches;
import org.chromium.chrome.browser.layouts.LayoutType;
import org.chromium.chrome.browser.tasks.ReturnToChromeUtil;
import org.chromium.chrome.test.ChromeJUnit4RunnerDelegate;
import org.chromium.chrome.test.ChromeTabbedActivityTestRule;
import org.chromium.chrome.test.util.browser.Features.EnableFeatures;
import org.chromium.content_public.browser.test.util.TestThreadUtils;
import org.chromium.ui.test.util.UiRestriction;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Integration tests of the {@link StartSurface} for cases where there are no tabs. See {@link
 * StartSurfaceTest} for test that have tabs.
 */
@RunWith(ParameterizedRunner.class)
@UseRunnerDelegate(ChromeJUnit4RunnerDelegate.class)
@Restriction(
        {UiRestriction.RESTRICTION_TYPE_PHONE, Restriction.RESTRICTION_TYPE_NON_LOW_END_DEVICE})
@EnableFeatures({ChromeFeatureList.START_SURFACE_ANDROID + "<Study"})
@DoNotBatch(reason = "StartSurface*Test tests startup behaviours and thus can't be batched.")
@CommandLineFlags.
Add({ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE, "force-fieldtrials=Study/Group"})
public class StartSurfaceNoTabsTest {
    @ParameterAnnotations.ClassParameter
    private static List<ParameterSet> sClassParams =
            Arrays.asList(new ParameterSet().value(false, false).name("NoInstant_NoReturn"),
                    new ParameterSet().value(true, false).name("Instant_NoReturn"),
                    new ParameterSet().value(false, true).name("NoInstant_Return"),
                    new ParameterSet().value(true, true).name("Instant_Return"));

    private static final String BASE_PARAMS = "force-fieldtrial-params=Study.Group:";

    @Rule
    public ChromeTabbedActivityTestRule mActivityTestRule = new ChromeTabbedActivityTestRule();

    private final boolean mImmediateReturn;

    public StartSurfaceNoTabsTest(boolean useInstantStart, boolean immediateReturn) {
        CachedFeatureFlags.setForTesting(ChromeFeatureList.INSTANT_START, useInstantStart);

        mImmediateReturn = immediateReturn;
    }

    @Before
    public void setUp() throws IOException {
        if (mImmediateReturn) {
            TAB_SWITCHER_ON_RETURN_MS.setForTesting(0);
            assertEquals(0, ReturnToChromeUtil.TAB_SWITCHER_ON_RETURN_MS.getValue());
            assertTrue(ReturnToChromeUtil.shouldShowTabSwitcher(-1));
        } else {
            assertFalse(ReturnToChromeUtil.shouldShowTabSwitcher(-1));
        }

        mActivityTestRule.startMainActivityFromLauncher();
    }

    @Test
    @LargeTest
    @Feature({"StartSurface"})
    // clang-format off
    @CommandLineFlags.Add({BASE_PARAMS + "tab_count_button_on_start_surface/true"})
    @DisabledTest(message = "https://crbug.com/1263910")
    public void testShow_SingleAsHomepage_NoTabs() throws TimeoutException {
        // clang-format on
        CriteriaHelper.pollUiThread(
                ()
                        -> mActivityTestRule.getActivity().getLayoutManager() != null
                        && mActivityTestRule.getActivity().getLayoutManager().isLayoutVisible(
                                LayoutType.TAB_SWITCHER));

        onView(withId(R.id.primary_tasks_surface_view)).check(matches(isDisplayed()));
        onView(withId(R.id.search_box_text)).check(matches(isDisplayed()));
        onView(withId(R.id.mv_tiles_container)).check(matches(isDisplayed()));
        onView(withId(R.id.tab_switcher_title)).check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.carousel_tab_switcher_container))
                .check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.more_tabs)).check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.tasks_surface_body)).check(matches(isDisplayed()));
        onView(withId(R.id.start_tab_switcher_button)).check(matches(isDisplayed()));
        onViewWaiting(withId(R.id.logo)).check(matches(isDisplayed()));

        onView(withId(R.id.start_tab_switcher_button))
                .perform(clickAndPressBackIfAccidentallyLongClicked());
        onViewWaiting(withId(R.id.secondary_tasks_surface_view));
        pressBack();
        onViewWaiting(withId(R.id.primary_tasks_surface_view));
    }

    @Test
    @MediumTest
    @Feature({"StartSurface"})
    // clang-format off
    @DisabledTest(message = "https://crbug.com/1263910")
    public void testShow_SingleAsHomepage_SingleTabSwitcher_NoTabs() {
        // clang-format on
        CriteriaHelper.pollUiThread(
                ()
                        -> mActivityTestRule.getActivity().getLayoutManager() != null
                        && mActivityTestRule.getActivity().getLayoutManager().isLayoutVisible(
                                LayoutType.TAB_SWITCHER));

        onView(withId(R.id.primary_tasks_surface_view)).check(matches(isDisplayed()));
        onView(withId(R.id.search_box_text)).check(matches(isDisplayed()));
        onView(withId(R.id.mv_tiles_container)).check(matches(isDisplayed()));
        onView(withId(R.id.tab_switcher_title)).check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.carousel_tab_switcher_container))
                .check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.single_tab_view)).check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.more_tabs)).check(matches(withEffectiveVisibility(GONE)));
        onView(withId(R.id.tasks_surface_body)).check(matches(isDisplayed()));
    }

    private void pressBack() {
        // ChromeTabbedActivity expects the native libraries to be loaded when back is pressed.
        mActivityTestRule.waitForActivityNativeInitializationComplete();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> mActivityTestRule.getActivity().onBackPressed());
    }

    private static GeneralClickAction clickAndPressBackIfAccidentallyLongClicked() {
        // If the click is misinterpreted as a long press, do a pressBack() to dismiss a context
        // menu.
        return new GeneralClickAction(
                Tap.SINGLE, GeneralLocation.CENTER, Press.FINGER, ViewActions.pressBack());
    }
}
