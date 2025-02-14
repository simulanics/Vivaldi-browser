// Copyright 2021 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.signin;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.support.test.runner.lifecycle.Stage;

import androidx.test.filters.MediumTest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import org.chromium.base.Promise;
import org.chromium.base.supplier.OneshotSupplierImpl;
import org.chromium.base.test.params.ParameterAnnotations;
import org.chromium.base.test.params.ParameterProvider;
import org.chromium.base.test.params.ParameterSet;
import org.chromium.base.test.params.ParameterizedRunner;
import org.chromium.base.test.util.ApplicationTestUtils;
import org.chromium.base.test.util.CommandLineFlags;
import org.chromium.base.test.util.CriteriaHelper;
import org.chromium.base.test.util.Feature;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.firstrun.FirstRunPageDelegate;
import org.chromium.chrome.browser.firstrun.PolicyLoadListener;
import org.chromium.chrome.browser.flags.ChromeSwitches;
import org.chromium.chrome.browser.night_mode.ChromeNightModeTestUtils;
import org.chromium.chrome.browser.privacy.settings.PrivacyPreferencesManagerImpl;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.SigninFirstRunFragmentTest.CustomSigninFirstRunFragment;
import org.chromium.chrome.browser.signin.services.FREMobileIdentityConsistencyFieldTrial;
import org.chromium.chrome.browser.signin.services.FREMobileIdentityConsistencyFieldTrial.VariationsGroup;
import org.chromium.chrome.browser.signin.services.IdentityServicesProvider;
import org.chromium.chrome.browser.signin.services.SigninChecker;
import org.chromium.chrome.browser.signin.services.SigninManager;
import org.chromium.chrome.test.ChromeJUnit4RunnerDelegate;
import org.chromium.chrome.test.ChromeTabbedActivityTestRule;
import org.chromium.chrome.test.util.ActivityTestUtils;
import org.chromium.chrome.test.util.browser.signin.AccountManagerTestRule;
import org.chromium.components.externalauth.ExternalAuthUtils;
import org.chromium.components.signin.identitymanager.IdentityManager;
import org.chromium.content_public.browser.test.util.TestThreadUtils;
import org.chromium.ui.test.util.RenderTestRule;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Render tests for the class {@link SigninFirstRunFragment}. */
@RunWith(ParameterizedRunner.class)
@ParameterAnnotations.UseRunnerDelegate(ChromeJUnit4RunnerDelegate.class)
@CommandLineFlags.Add({ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE})
public class SigninFirstRunFragmentRenderTest {
    /** Parameter provider for night mode state and device orientation. */
    public static class NightModeAndOrientationParameterProvider implements ParameterProvider {
        private static List<ParameterSet> sParams = Arrays.asList(
                new ParameterSet()
                        .value(/*nightModeEnabled=*/false, Configuration.ORIENTATION_PORTRAIT)
                        .name("NightModeDisabled_Portrait"),
                new ParameterSet()
                        .value(/*nightModeEnabled=*/false, Configuration.ORIENTATION_LANDSCAPE)
                        .name("NightModeDisabled_Landscape"),
                new ParameterSet()
                        .value(/*nightModeEnabled=*/true, Configuration.ORIENTATION_PORTRAIT)
                        .name("NightModeEnabled_Portrait"),
                new ParameterSet()
                        .value(/*nightModeEnabled=*/true, Configuration.ORIENTATION_LANDSCAPE)
                        .name("NightModeEnabled_Landscape"));
        @Override
        public Iterable<ParameterSet> getParameters() {
            return sParams;
        }
    }

    private static final String TEST_EMAIL1 = "test.account1@gmail.com";
    private static final String CHILD_ACCOUNT_NAME =
            AccountManagerTestRule.generateChildEmail("account@gmail.com");

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public final RenderTestRule mRenderTestRule =
            RenderTestRule.Builder.withPublicCorpus()
                    .setBugComponent(RenderTestRule.Component.UI_BROWSER_FIRST_RUN)
                    .build();

    @Rule
    public final AccountManagerTestRule mAccountManagerTestRule = new AccountManagerTestRule();

    @Rule
    public final ChromeTabbedActivityTestRule mChromeActivityTestRule =
            new ChromeTabbedActivityTestRule();

    @Mock
    private ExternalAuthUtils mExternalAuthUtilsMock;
    @Mock
    private FirstRunPageDelegate mFirstRunPageDelegateMock;
    @Mock
    private PolicyLoadListener mPolicyLoadListenerMock;
    @Mock
    private SigninManager mSigninManagerMock;
    @Mock
    private SigninChecker mSigninCheckerMock;
    @Mock
    private IdentityManager mIdentityManagerMock;
    @Mock
    private IdentityServicesProvider mIdentityServicesProviderMock;
    @Mock
    private PrivacyPreferencesManagerImpl mPrivacyPreferencesManagerMock;

    private CustomSigninFirstRunFragment mFragment;

    @ParameterAnnotations.UseMethodParameterBefore(NightModeAndOrientationParameterProvider.class)
    public void setupNightModeAndDeviceOrientation(boolean nightModeEnabled, int orientation) {
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            ChromeNightModeTestUtils.setUpNightModeForChromeActivity(nightModeEnabled);
        });
        mRenderTestRule.setNightModeEnabled(nightModeEnabled);
        mRenderTestRule.setVariantPrefix(
                orientation == Configuration.ORIENTATION_PORTRAIT ? "Portrait" : "Landscape");
    }

    @BeforeClass
    public static void setUpBeforeActivityLaunched() {
        ChromeNightModeTestUtils.setUpNightModeBeforeChromeActivityLaunched();
    }

    @Before
    public void setUp() {
        when(mExternalAuthUtilsMock.canUseGooglePlayServices()).thenReturn(true);
        ExternalAuthUtils.setInstanceForTesting(mExternalAuthUtilsMock);
        SigninCheckerProvider.setForTests(mSigninCheckerMock);
        FREMobileIdentityConsistencyFieldTrial.setFirstRunVariationsTrialGroupForTesting(
                VariationsGroup.DEFAULT);
        when(mPolicyLoadListenerMock.get()).thenReturn(false);
        when(mFirstRunPageDelegateMock.getPolicyLoadListener()).thenReturn(mPolicyLoadListenerMock);
        when(mFirstRunPageDelegateMock.canUseLandscapeLayout()).thenReturn(true);
        mChromeActivityTestRule.startMainActivityOnBlankPage();
        mFragment = new CustomSigninFirstRunFragment();
        mFragment.setPageDelegate(mFirstRunPageDelegateMock);
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            Promise<Void> nativeSideIsInitialized = new Promise<>();
            nativeSideIsInitialized.fulfill(null);
            when(mFirstRunPageDelegateMock.getNativeInitializationPromise())
                    .thenReturn(nativeSideIsInitialized);

            OneshotSupplierImpl<Boolean> childAccountStatusListener = new OneshotSupplierImpl<>();
            childAccountStatusListener.set(false);
            when(mFirstRunPageDelegateMock.getChildAccountStatusSupplier())
                    .thenReturn(childAccountStatusListener);
        });
    }

    @AfterClass
    public static void tearDownAfterActivityDestroyed() {
        ChromeNightModeTestUtils.tearDownNightModeAfterChromeActivityDestroyed();
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    public void testFragmentRotationToLandscapeWithAccount() throws IOException {
        mAccountManagerTestRule.addAccount(TEST_EMAIL1);
        launchActivityWithFragment(Configuration.ORIENTATION_PORTRAIT);

        ActivityTestUtils.rotateActivityToOrientation(
                mChromeActivityTestRule.getActivity(), Configuration.ORIENTATION_LANDSCAPE);
        CriteriaHelper.pollUiThread(() -> {
            return mFragment.getView().findViewById(R.id.account_text_secondary).isShown();
        });
        mRenderTestRule.render(
                mFragment.getView(), "signin_first_run_fragment_with_account_landscape");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    public void testFragmentRotationToPortraitWithAccount() throws IOException {
        mAccountManagerTestRule.addAccount(TEST_EMAIL1);
        launchActivityWithFragment(Configuration.ORIENTATION_LANDSCAPE);

        ActivityTestUtils.rotateActivityToOrientation(
                mChromeActivityTestRule.getActivity(), Configuration.ORIENTATION_PORTRAIT);
        CriteriaHelper.pollUiThread(() -> {
            return mFragment.getView().findViewById(R.id.account_text_secondary).isShown();
        });
        mRenderTestRule.render(
                mFragment.getView(), "signin_first_run_fragment_with_account_portrait");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragmentWithAccount(boolean nightModeEnabled, int orientation)
            throws IOException {
        mAccountManagerTestRule.addAccount(TEST_EMAIL1);

        launchActivityWithFragment(orientation);

        CriteriaHelper.pollUiThread(() -> {
            return mFragment.getView().findViewById(R.id.account_text_secondary).isShown();
        });
        mRenderTestRule.render(mFragment.getView(), "signin_first_run_fragment_with_account");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragmentWithAccountOnManagedDevice(boolean nightModeEnabled, int orientation)
            throws IOException {
        when(mPolicyLoadListenerMock.get()).thenReturn(true);
        mAccountManagerTestRule.addAccount(TEST_EMAIL1);

        launchActivityWithFragment(orientation);

        CriteriaHelper.pollUiThread(() -> {
            return mFragment.getView().findViewById(R.id.account_text_secondary).isShown();
        });
        mRenderTestRule.render(
                mFragment.getView(), "signin_first_run_fragment_with_account_managed");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragmentWithAccountWhenSigninIsDisabledByPolicy(
            boolean nightModeEnabled, int orientation) throws IOException {
        IdentityServicesProvider.setInstanceForTests(mIdentityServicesProviderMock);
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            when(IdentityServicesProvider.get().getSigninManager(
                         Profile.getLastUsedRegularProfile()))
                    .thenReturn(mSigninManagerMock);
            when(IdentityServicesProvider.get().getIdentityManager(
                         Profile.getLastUsedRegularProfile()))
                    .thenReturn(mIdentityManagerMock);
        });
        when(mSigninManagerMock.isSigninDisabledByPolicy()).thenReturn(true);
        when(mPolicyLoadListenerMock.get()).thenReturn(true);
        mAccountManagerTestRule.addAccount(TEST_EMAIL1);

        launchActivityWithFragment(orientation);

        mRenderTestRule.render(
                mFragment.getView(), "signin_first_run_fragment_when_signin_disabled_by_policy");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragmentWithoutAccount(boolean nightModeEnabled, int orientation)
            throws IOException {
        launchActivityWithFragment(orientation);

        mRenderTestRule.render(mFragment.getView(), "signin_first_run_fragment_without_account");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragmentWithoutAccountOnManagedDevice(boolean nightModeEnabled, int orientation)
            throws IOException {
        when(mPolicyLoadListenerMock.get()).thenReturn(true);
        launchActivityWithFragment(orientation);

        mRenderTestRule.render(
                mFragment.getView(), "signin_first_run_fragment_without_account_managed");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragmentWithChildAccount(boolean nightModeEnabled, int orientation)
            throws IOException {
        mAccountManagerTestRule.addAccount(CHILD_ACCOUNT_NAME);

        launchActivityWithFragment(orientation);

        CriteriaHelper.pollUiThread(() -> {
            return mFragment.getView().findViewById(R.id.account_text_secondary).isShown();
        });
        mRenderTestRule.render(mFragment.getView(), "signin_first_run_fragment_with_child_account");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragmentWhenCannotUseGooglePlayService(
            boolean nightModeEnabled, int orientation) throws IOException {
        when(mExternalAuthUtilsMock.canUseGooglePlayServices()).thenReturn(false);

        launchActivityWithFragment(orientation);

        mRenderTestRule.render(
                mFragment.getView(), "signin_first_run_fragment_signin_not_supported");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragmentWhenMetricsReportingIsDisabledByPolicy(
            boolean nightModeEnabled, int orientation) throws IOException {
        when(mPolicyLoadListenerMock.get()).thenReturn(true);
        when(mPrivacyPreferencesManagerMock.isUsageAndCrashReportingPermittedByPolicy())
                .thenReturn(false);

        PrivacyPreferencesManagerImpl.setInstanceForTesting(mPrivacyPreferencesManagerMock);

        launchActivityWithFragment(orientation);

        mRenderTestRule.render(mFragment.getView(),
                "signin_first_run_fragment_when_metrics_reporting_is_disabled_by_policy");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragmentWhenMetricsReportingIsDisabledByPolicyWithAccount(
            boolean nightModeEnabled, int orientation) throws IOException {
        when(mPolicyLoadListenerMock.get()).thenReturn(true);
        when(mPrivacyPreferencesManagerMock.isUsageAndCrashReportingPermittedByPolicy())
                .thenReturn(false);

        PrivacyPreferencesManagerImpl.setInstanceForTesting(mPrivacyPreferencesManagerMock);

        mAccountManagerTestRule.addAccount(TEST_EMAIL1);

        launchActivityWithFragment(orientation);

        CriteriaHelper.pollUiThread(() -> {
            return mFragment.getView().findViewById(R.id.account_text_secondary).isShown();
        });
        mRenderTestRule.render(mFragment.getView(),
                "signin_first_run_fragment_when_metrics_reporting_is_disabled_by_policy_with_account");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragmentWhenMetricsReportingIsDisabledByPolicyWithChildAccount(
            boolean nightModeEnabled, int orientation) throws IOException {
        when(mPolicyLoadListenerMock.get()).thenReturn(true);
        when(mPrivacyPreferencesManagerMock.isUsageAndCrashReportingPermittedByPolicy())
                .thenReturn(false);

        PrivacyPreferencesManagerImpl.setInstanceForTesting(mPrivacyPreferencesManagerMock);

        mAccountManagerTestRule.addAccount(CHILD_ACCOUNT_NAME);

        launchActivityWithFragment(orientation);

        CriteriaHelper.pollUiThread(() -> {
            return mFragment.getView().findViewById(R.id.account_text_secondary).isShown();
        });
        mRenderTestRule.render(mFragment.getView(),
                "signin_first_run_fragment_when_metrics_reporting_is_disabled_by_policy_with_child_account");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragment_WelcomeToChrome(boolean nightModeEnabled, int orientation)
            throws IOException {
        FREMobileIdentityConsistencyFieldTrial.setFirstRunVariationsTrialGroupForTesting(
                VariationsGroup.WELCOME_TO_CHROME);
        launchActivityWithFragment(orientation);

        mRenderTestRule.render(mFragment.getView(), "signin_first_run_fragment_welcome_to_chrome");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragment_WelcomeToChrome_MostOutOfChrome(
            boolean nightModeEnabled, int orientation) throws IOException {
        FREMobileIdentityConsistencyFieldTrial.setFirstRunVariationsTrialGroupForTesting(
                VariationsGroup.WELCOME_TO_CHROME_MOST_OUT_OF_CHROME);
        launchActivityWithFragment(orientation);

        mRenderTestRule.render(mFragment.getView(),
                "signin_first_run_fragment_welcome_to_chrome_most_out_of_chrome");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragment_WelcomeToChrome_StrongestSecurity(
            boolean nightModeEnabled, int orientation) throws IOException {
        FREMobileIdentityConsistencyFieldTrial.setFirstRunVariationsTrialGroupForTesting(
                VariationsGroup.WELCOME_TO_CHROME_STRONGEST_SECURITY);
        launchActivityWithFragment(orientation);

        mRenderTestRule.render(mFragment.getView(),
                "signin_first_run_fragment_welcome_to_chrome_strongest_security");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragment_WelcomeToChrome_EasierAcrossDevices(
            boolean nightModeEnabled, int orientation) throws IOException {
        FREMobileIdentityConsistencyFieldTrial.setFirstRunVariationsTrialGroupForTesting(
                VariationsGroup.WELCOME_TO_CHROME_EASIER_ACROSS_DEVICES);
        launchActivityWithFragment(orientation);

        mRenderTestRule.render(mFragment.getView(),
                "signin_first_run_fragment_welcome_to_chrome_easier_across_devices");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragment_MostOutOfChrome(boolean nightModeEnabled, int orientation)
            throws IOException {
        FREMobileIdentityConsistencyFieldTrial.setFirstRunVariationsTrialGroupForTesting(
                VariationsGroup.MOST_OUT_OF_CHROME);
        launchActivityWithFragment(orientation);

        mRenderTestRule.render(mFragment.getView(), "signin_first_run_fragment_most_out_chrome");
    }

    @Test
    @MediumTest
    @Feature("RenderTest")
    @ParameterAnnotations.UseMethodParameter(NightModeAndOrientationParameterProvider.class)
    public void testFragment_MakeChromeYourOwn(boolean nightModeEnabled, int orientation)
            throws IOException {
        FREMobileIdentityConsistencyFieldTrial.setFirstRunVariationsTrialGroupForTesting(
                VariationsGroup.MAKE_CHROME_YOUR_OWN);
        launchActivityWithFragment(orientation);

        mRenderTestRule.render(
                mFragment.getView(), "signin_first_run_fragment_make_chrome_your_own");
    }

    private void launchActivityWithFragment(int orientation) {
        ActivityTestUtils.rotateActivityToOrientation(
                mChromeActivityTestRule.getActivity(), orientation);
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            mChromeActivityTestRule.getActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, mFragment)
                    .commit();
        });
        ApplicationTestUtils.waitForActivityState(
                mChromeActivityTestRule.getActivity(), Stage.RESUMED);
        // Parts of SigninFirstRunFragment are initialized asynchronously, so ensure the load
        // spinner is not displayed before grabbing a screenshot.
        onView(withId(R.id.fre_native_and_policy_load_progress_spinner))
                .check(matches(not(isDisplayed())));
    }
}
