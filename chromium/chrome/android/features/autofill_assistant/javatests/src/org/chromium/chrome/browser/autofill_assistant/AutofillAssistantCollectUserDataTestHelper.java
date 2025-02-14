// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant;

import static org.chromium.chrome.browser.autofill_assistant.AutofillAssistantUiTestUtil.findViewsWithTag;
import static org.chromium.components.autofill_assistant.AssistantTagsForTesting.COLLECT_USER_DATA_CHOICE_LIST;
import static org.chromium.components.autofill_assistant.user_data.AssistantCollectUserDataCoordinator.DIVIDER_TAG;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.chromium.base.test.util.CallbackHelper;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.CreditCard;
import org.chromium.components.autofill_assistant.AssistantAutofillProfile;
import org.chromium.components.autofill_assistant.AssistantOptionModel;
import org.chromium.components.autofill_assistant.AssistantPaymentInstrument;
import org.chromium.components.autofill_assistant.AssistantTagsForTesting;
import org.chromium.components.autofill_assistant.generic_ui.AssistantValue;
import org.chromium.components.autofill_assistant.user_data.AssistantChoiceList;
import org.chromium.components.autofill_assistant.user_data.AssistantCollectUserDataCoordinator;
import org.chromium.components.autofill_assistant.user_data.AssistantCollectUserDataDelegate;
import org.chromium.components.autofill_assistant.user_data.AssistantCollectUserDataModel;
import org.chromium.components.autofill_assistant.user_data.AssistantLoginChoice;
import org.chromium.components.autofill_assistant.user_data.AssistantTermsAndConditionsState;
import org.chromium.components.autofill_assistant.user_data.AssistantUserDataEventType;
import org.chromium.components.autofill_assistant.user_data.AssistantVerticalExpander;
import org.chromium.components.autofill_assistant.user_data.AssistantVerticalExpanderAccordion;
import org.chromium.content_public.browser.test.util.TestThreadUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Helper class for testing autofill assistant payment request. Code adapted from
 * https://cs.chromium.org/chromium/src/chrome/android/javatests/src/org/chromium/chrome/browser/autofill/AutofillTestHelper.java
 */
public class AutofillAssistantCollectUserDataTestHelper {
    private final CallbackHelper mOnPersonalDataChangedHelper = new CallbackHelper();

    /** Extracts the views from a coordinator. */
    static class ViewHolder {
        final AssistantVerticalExpanderAccordion mAccordion;
        final AssistantVerticalExpander mContactSection;
        final AssistantVerticalExpander mPhoneNumberSection;
        final AssistantVerticalExpander mPaymentSection;
        final AssistantVerticalExpander mShippingSection;
        final AssistantVerticalExpander mLoginsSection;
        final LinearLayout mTermsSection;
        final TextView mInfoSection;
        final AssistantChoiceList mContactList;
        final AssistantChoiceList mPhoneNumberList;
        final AssistantChoiceList mPaymentMethodList;
        final AssistantChoiceList mShippingAddressList;
        final AssistantChoiceList mLoginList;
        final List<View> mDividers;
        final RelativeLayout mDataOriginNotice;

        ViewHolder(AssistantCollectUserDataCoordinator coordinator) {
            mAccordion = coordinator.getView().findViewWithTag(
                    AssistantTagsForTesting.COLLECT_USER_DATA_ACCORDION_TAG);
            mContactSection = coordinator.getView().findViewWithTag(
                    AssistantTagsForTesting.COLLECT_USER_DATA_CONTACT_DETAILS_SECTION_TAG);
            mPhoneNumberSection = coordinator.getView().findViewWithTag(
                    AssistantTagsForTesting.COLLECT_USER_DATA_PHONE_NUMBER_SECTION_TAG);
            mPaymentSection = coordinator.getView().findViewWithTag(
                    AssistantTagsForTesting.COLLECT_USER_DATA_PAYMENT_METHOD_SECTION_TAG);
            mShippingSection = coordinator.getView().findViewWithTag(
                    AssistantTagsForTesting.COLLECT_USER_DATA_SHIPPING_ADDRESS_SECTION_TAG);
            mLoginsSection = coordinator.getView().findViewWithTag(
                    AssistantTagsForTesting.COLLECT_USER_DATA_LOGIN_SECTION_TAG);
            mTermsSection = coordinator.getView().findViewWithTag(
                    AssistantTagsForTesting.COLLECT_USER_DATA_RADIO_TERMS_SECTION_TAG);
            mInfoSection = coordinator.getView().findViewWithTag(
                    AssistantTagsForTesting.COLLECT_USER_DATA_INFO_SECTION_TAG);
            mDataOriginNotice = coordinator.getView().findViewWithTag(
                    AssistantTagsForTesting.COLLECT_USER_DATA_DATA_ORIGIN_NOTICE_TAG);
            mDividers = findViewsWithTag(coordinator.getView(), DIVIDER_TAG);
            mContactList = (AssistantChoiceList) (findViewsWithTag(
                    mContactSection, COLLECT_USER_DATA_CHOICE_LIST)
                                                          .get(0));
            mPhoneNumberList = (AssistantChoiceList) (findViewsWithTag(
                    mPhoneNumberSection, COLLECT_USER_DATA_CHOICE_LIST)
                                                              .get(0));
            mPaymentMethodList = (AssistantChoiceList) (findViewsWithTag(
                    mPaymentSection, COLLECT_USER_DATA_CHOICE_LIST)
                                                                .get(0));
            mShippingAddressList = (AssistantChoiceList) (findViewsWithTag(
                    mShippingSection, COLLECT_USER_DATA_CHOICE_LIST)
                                                                  .get(0));
            mLoginList = (AssistantChoiceList) (findViewsWithTag(
                    mLoginsSection, COLLECT_USER_DATA_CHOICE_LIST)
                                                        .get(0));
        }
    }

    /**
     * Simple mock delegate which stores the currently selected PR items.
     *  TODO(crbug.com/860868): Remove this once PR is fully a MVC component, in which case one
     *  should be able to get the currently selected items by asking the model.
     */
    static class MockDelegate implements AssistantCollectUserDataDelegate {
        AssistantAutofillProfile mContact;
        AssistantAutofillProfile mPhoneNumber;
        AssistantAutofillProfile mShippingAddress;
        AssistantPaymentInstrument mPaymentInstrument;
        AssistantLoginChoice mLoginChoice;

        @AssistantTermsAndConditionsState
        int mTermsStatus;
        @Nullable
        Integer mLastLinkClicked;
        Map<String, AssistantValue> mAdditionalValues = new HashMap<>();

        @Override
        public void onContactInfoChanged(@Nullable AssistantOptionModel.ContactModel contactModel,
                @AssistantUserDataEventType int eventType) {
            mContact = contactModel == null ? null : contactModel.mOption;
        }

        @Override
        public void onPhoneNumberChanged(@Nullable AssistantOptionModel.ContactModel contactModel,
                @AssistantUserDataEventType int eventType) {
            mPhoneNumber = contactModel == null ? null : contactModel.mOption;
        }

        @Override
        public void onShippingAddressChanged(
                @Nullable AssistantOptionModel.AddressModel addressModel,
                @AssistantUserDataEventType int eventType) {
            mShippingAddress = addressModel == null ? null : addressModel.mOption;
        }

        @Override
        public void onPaymentMethodChanged(
                @Nullable AssistantOptionModel.PaymentInstrumentModel paymentInstrumentModel,
                @AssistantUserDataEventType int eventType) {
            mPaymentInstrument =
                    paymentInstrumentModel == null ? null : paymentInstrumentModel.mOption;
        }

        @Override
        public void onTermsAndConditionsChanged(@AssistantTermsAndConditionsState int state) {
            mTermsStatus = state;
        }

        @Override
        public void onLoginChoiceChanged(
                @Nullable AssistantCollectUserDataModel.LoginChoiceModel loginChoiceModel,
                @AssistantUserDataEventType int eventType) {
            mLoginChoice = loginChoiceModel == null ? null : loginChoiceModel.mOption;
        }

        @Override
        public void onTextLinkClicked(int link) {
            mLastLinkClicked = link;
        }

        @Override
        public void onKeyValueChanged(String key, AssistantValue value) {
            mAdditionalValues.put(key, value);
        }

        @Override
        public void onInputTextFocusChanged(boolean isFocused) {}
    }

    public AutofillAssistantCollectUserDataTestHelper() throws TimeoutException {
        registerDataObserver();
        setRequestTimeoutForTesting();
        setSyncServiceForTesting();
    }

    private void setRequestTimeoutForTesting() {
        TestThreadUtils.runOnUiThreadBlocking(
                () -> PersonalDataManager.setRequestTimeoutForTesting(0));
    }

    private void setSyncServiceForTesting() {
        TestThreadUtils.runOnUiThreadBlocking(
                () -> PersonalDataManager.getInstance().setSyncServiceForTesting());
    }

    /**
     * Add a new profile to the PersonalDataManager.
     *
     * @param profile The profile to add.
     * @return the GUID of the created profile.
     */
    public String setProfile(final AutofillProfile profile) throws TimeoutException {
        int callCount = mOnPersonalDataChangedHelper.getCallCount();
        String guid = TestThreadUtils.runOnUiThreadBlockingNoException(
                () -> PersonalDataManager.getInstance().setProfile(profile));
        mOnPersonalDataChangedHelper.waitForCallback(callCount);
        return guid;
    }

    /**
     * Delete a profile from the PersonalDataManager.
     *
     * @param guid The GUID of the profile to delete.
     */
    public void deleteProfile(String guid) throws TimeoutException {
        int callCount = mOnPersonalDataChangedHelper.getCallCount();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> PersonalDataManager.getInstance().deleteProfile(guid));
        mOnPersonalDataChangedHelper.waitForCallback(callCount);
    }

    /**
     * Adds a new profile with dummy data to the PersonalDataManager.
     *
     * @param fullName The full name for the profile to create.
     * @param email The email for the profile to create.
     * @param postcode The postcode of the billing address.
     * @return the GUID of the created profile.
     */
    public String addDummyProfile(String fullName, String email, String postcode)
            throws TimeoutException {
        PersonalDataManager.AutofillProfile profile = createDummyProfile(fullName, email, postcode);
        return setProfile(profile);
    }

    /**
     * Add a new profile with dummy data to the PersonalDataManager.
     *
     * @param fullName The full name for the profile to create.
     * @param email The email for the profile to create.
     * @return the GUID of the created profile.
     */
    public String addDummyProfile(String fullName, String email) throws TimeoutException {
        return addDummyProfile(fullName, email, "90210");
    }

    /**
     * Create a new profile.
     *
     * @param fullName The full name for the profile to create.
     * @param email The email for the profile to create.
     * @param postcode The postcode of the billing address.
     * @return the profile.
     */
    public PersonalDataManager.AutofillProfile createDummyProfile(
            String fullName, String email, String postcode) {
        return new PersonalDataManager.AutofillProfile(/* guid= */ "", "https://www.example.com",
                /* honorificPrefix= */ "", fullName, "Acme Inc.", "123 Main", "California",
                "Los Angeles",
                /* dependentLocality= */ "", postcode, /* sortingCode= */ "", "UZ",
                /* phoneNumber= */ "", email, /* languageCode= */ "");
    }

    /**
     * Create a new profile.
     *
     * @param fullName The full name for the profile to create.
     * @param email The email for the profile to create.
     * @return the profile.
     */
    public PersonalDataManager.AutofillProfile createDummyProfile(String fullName, String email) {
        return createDummyProfile(fullName, email, "90210");
    }

    /**
     * Add a new local credit card to the PersonalDataManager.
     *
     * @param card The credit card to add.
     * @return the GUID of the created credit card.
     */
    public String setCreditCard(final CreditCard card) throws TimeoutException {
        assert card.getIsLocal();
        int callCount = mOnPersonalDataChangedHelper.getCallCount();
        String guid = TestThreadUtils.runOnUiThreadBlockingNoException(
                () -> PersonalDataManager.getInstance().setCreditCard(card));
        mOnPersonalDataChangedHelper.waitForCallback(callCount);
        return guid;
    }

    /**
     * Add a new server credit card to the PersonalDataManager.
     *
     * @param card The credit card to add.
     */
    public void addServerCreditCard(CreditCard card) throws TimeoutException {
        assert !card.getIsLocal();
        int callCount = mOnPersonalDataChangedHelper.getCallCount();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> PersonalDataManager.getInstance().addServerCreditCardForTest(card));
        mOnPersonalDataChangedHelper.waitForCallback(callCount);
    }

    /**
     * Add a credit card with dummy data to the PersonalDataManager.
     *
     * @param billingAddressId The billing address profile GUID.
     * @return the GUID of the created credit card
     */
    public String addDummyCreditCard(String billingAddressId) throws TimeoutException {
        return setCreditCard(createDummyCreditCard(billingAddressId));
    }

    /**
     * Add a credit card with dummy data to the PersonalDataManager.
     *
     * @param billingAddressId The billing address profile GUID.
     * @param cardNumber The card number.
     * @return the GUID of the created credit card
     */
    public String addDummyCreditCard(String billingAddressId, String cardNumber)
            throws TimeoutException {
        return setCreditCard(createDummyCreditCard(billingAddressId, cardNumber));
    }

    /**
     * Delete a credit card from the PersonalDataManager.
     *
     * @param guid The GUID of the credit card to delete.
     */
    public void deleteCreditCard(String guid) throws TimeoutException {
        int callCount = mOnPersonalDataChangedHelper.getCallCount();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> PersonalDataManager.getInstance().deleteCreditCard(guid));
        mOnPersonalDataChangedHelper.waitForCallback(callCount);
    }

    /**
     * Create a credit card with dummy data.
     *
     * @param billingAddressId The billing address profile GUID.
     * @param cardNumber The card number.
     * @param isLocal Whether the card is local or not.
     * @return the card.
     */
    public CreditCard createDummyCreditCard(
            String billingAddressId, String cardNumber, boolean isLocal) {
        String profileName = TestThreadUtils.runOnUiThreadBlockingNoException(
                () -> PersonalDataManager.getInstance().getProfile(billingAddressId).getFullName());

        return new CreditCard("", "https://example.com", /* isLocal = */ isLocal, true, profileName,
                cardNumber, "1111", "12", "2050", "visa",
                org.chromium.components.autofill_assistant.R.drawable.visa_card, billingAddressId,
                /* serverId= */ "");
    }

    /**
     * Create a credit card with dummy data.
     *
     * @param billingAddressId The billing address profile GUID.
     * @param cardNumber The card number.
     * @return the card.
     */
    public CreditCard createDummyCreditCard(String billingAddressId, String cardNumber) {
        return createDummyCreditCard(billingAddressId, cardNumber, /* isLocal = */ true);
    }

    /**
     * Create a credit card with dummy data.
     *
     * @param billingAddressId The billing address profile GUID.
     * @return the card.
     */
    public CreditCard createDummyCreditCard(String billingAddressId) {
        return createDummyCreditCard(billingAddressId, "4111111111111111");
    }

    private void registerDataObserver() throws TimeoutException {
        int callCount = mOnPersonalDataChangedHelper.getCallCount();
        boolean isDataLoaded = TestThreadUtils.runOnUiThreadBlockingNoException(
                ()
                        -> PersonalDataManager.getInstance().registerDataObserver(
                                () -> mOnPersonalDataChangedHelper.notifyCalled()));
        if (isDataLoaded) return;
        mOnPersonalDataChangedHelper.waitForCallback(callCount);
    }
}
