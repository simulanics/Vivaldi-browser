// Copyright 2021 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync.ui;

import android.app.Activity;
import android.content.res.Resources;

import androidx.annotation.VisibleForTesting;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.UnownedUserData;
import org.chromium.base.UnownedUserDataHost;
import org.chromium.base.UnownedUserDataKey;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.sync.SyncService;
import org.chromium.chrome.browser.sync.SyncService.SyncStateChangedListener;
import org.chromium.chrome.browser.sync.settings.SyncSettingsUtils;
import org.chromium.chrome.browser.sync.settings.SyncSettingsUtils.SyncError;
import org.chromium.chrome.browser.sync.ui.SyncErrorPromptUtils.SyncErrorPromptAction;
import org.chromium.chrome.browser.sync.ui.SyncErrorPromptUtils.SyncErrorPromptType;
import org.chromium.components.messages.DismissReason;
import org.chromium.components.messages.MessageBannerProperties;
import org.chromium.components.messages.MessageDispatcher;
import org.chromium.components.messages.MessageDispatcherProvider;
import org.chromium.components.messages.MessageIdentifier;
import org.chromium.components.messages.PrimaryActionClickBehavior;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.modelutil.PropertyModel;

/**
 * A message UI that informs the current sync error and contains a button to take action to resolve
 * it.
 * This class is tied to a window and at most one instance per window can exist at a time.
 * In practice however, because the time limit imposed between 2 displays is global,
 * only one instance in the whole application will exist at a time.
 */
public class SyncErrorMessage implements SyncStateChangedListener, UnownedUserData {
    private final @SyncErrorPromptType int mType;
    private final Activity mActivity;
    private final MessageDispatcher mMessageDispatcher;
    private final PropertyModel mModel;
    private static MessageDispatcher sMessageDispatcherForTesting;

    private static final UnownedUserDataKey<SyncErrorMessage> SYNC_ERROR_MESSAGE_KEY =
            new UnownedUserDataKey<>(SyncErrorMessage.class);

    /**
     * Creates a {@link SyncErrorMessage} in the window of |dispatcher|, or results in a no-op
     * if preconditions are not satisfied. The conditions are:
     * a) there is an ongoing sync error and it belongs to the subset defined by
     *    {@link SyncErrorPromptType}.
     * b) a minimal time interval has passed since the UI was last shown.
     * c) there is no other instance of the UI being shown on this window.
     * d) there is a valid {@link MessageDispatcher} in this window.
     *
     * @param windowAndroid The {@link WindowAndroid} to show and dismiss message UIs.
     */
    public static void maybeShowMessageUi(WindowAndroid windowAndroid) {
        try (TraceEvent t = TraceEvent.scoped("SyncErrorMessage.maybeShowMessageUi")) {
            if (!SyncErrorPromptUtils.shouldShowPrompt(SyncErrorPromptUtils.getSyncErrorUiType(
                        SyncSettingsUtils.getSyncError()))) {
                return;
            }

            MessageDispatcher dispatcher = MessageDispatcherProvider.from(windowAndroid);
            if (dispatcher == null) {
                // Show prompt UI next time when there is a valid dispatcher attached to this
                // window.
                return;
            }

            UnownedUserDataHost host = windowAndroid.getUnownedUserDataHost();
            if (SYNC_ERROR_MESSAGE_KEY.retrieveDataFromHost(host) != null) {
                // Show prompt UI next time when the previous message has disappeared.
                return;
            }
            SYNC_ERROR_MESSAGE_KEY.attachToHost(
                    host, new SyncErrorMessage(dispatcher, windowAndroid.getActivity().get()));
        }
    }

    private SyncErrorMessage(MessageDispatcher dispatcher, Activity activity) {
        @SyncError
        int error = SyncSettingsUtils.getSyncError();
        String errorMessage = SyncErrorPromptUtils.getErrorMessage(activity, error);
        String title = SyncErrorPromptUtils.getTitle(activity, error);
        String primaryButtonText = SyncErrorPromptUtils.getPrimaryButtonText(activity, error);
        Resources resources = activity.getResources();
        mModel = new PropertyModel.Builder(MessageBannerProperties.ALL_KEYS)
                         .with(MessageBannerProperties.MESSAGE_IDENTIFIER,
                                 MessageIdentifier.SYNC_ERROR)
                         .with(MessageBannerProperties.TITLE, title)
                         .with(MessageBannerProperties.DESCRIPTION, errorMessage)
                         .with(MessageBannerProperties.PRIMARY_BUTTON_TEXT, primaryButtonText)
                         .with(MessageBannerProperties.ICON,
                                 ApiCompatibilityUtils.getDrawable(
                                         resources, R.drawable.ic_sync_error_legacy_24dp))
                         .with(MessageBannerProperties.ICON_TINT_COLOR,
                                 activity.getColor(R.color.default_red))
                         .with(MessageBannerProperties.ON_PRIMARY_ACTION, this::onAccepted)
                         .with(MessageBannerProperties.ON_DISMISSED, this::onDismissed)
                         .build();
        mMessageDispatcher =
                sMessageDispatcherForTesting == null ? dispatcher : sMessageDispatcherForTesting;
        mMessageDispatcher.enqueueWindowScopedMessage(mModel, false);
        mType = SyncErrorPromptUtils.getSyncErrorUiType(error);
        mActivity = activity;
        SyncService.get().addSyncStateChangedListener(this);
        SyncErrorPromptUtils.updateLastShownTime();
        recordHistogram(SyncErrorPromptAction.SHOWN);
    }

    @Override
    public void syncStateChanged() {
        // If the error disappeared or changed type in the meantime, dismiss the UI.
        if (mType != SyncErrorPromptUtils.getSyncErrorUiType(SyncSettingsUtils.getSyncError())) {
            mMessageDispatcher.dismissMessage(mModel, DismissReason.UNKNOWN);
            assert !SYNC_ERROR_MESSAGE_KEY.isAttachedToAnyHost(this)
                : "Message UI should have been dismissed";
        }
    }

    private @PrimaryActionClickBehavior int onAccepted() {
        SyncErrorPromptUtils.onUserAccepted(mType, mActivity);
        recordHistogram(SyncErrorPromptAction.BUTTON_CLICKED);
        return PrimaryActionClickBehavior.DISMISS_IMMEDIATELY;
    }

    private void onDismissed(@DismissReason int reason) {
        if (reason != DismissReason.TIMER && reason != DismissReason.GESTURE
                && reason != DismissReason.PRIMARY_ACTION) {
            // If the user didn't explicitly accept/dismiss the message, and the display timeout
            // wasn't reached either, resetLastShownTime() so the message can be shown again. This
            // includes the case where the user changes tabs while the message is showing
            // (TAB_SWITCHED).
            SyncErrorPromptUtils.resetLastShownTime();
        }
        SyncService.get().removeSyncStateChangedListener(this);
        SYNC_ERROR_MESSAGE_KEY.detachFromAllHosts(this);

        // This metric should be recorded only on explicit dismissal.
        if (reason == DismissReason.GESTURE) {
            recordHistogram(SyncErrorPromptAction.DISMISSED);
        }
    }

    private void recordHistogram(@SyncErrorPromptAction int action) {
        assert mType != SyncErrorPromptType.NOT_SHOWN;
        String name = "Signin.SyncErrorMessage."
                + SyncErrorPromptUtils.getSyncErrorPromptUiHistogramSuffix(mType);
        RecordHistogram.recordEnumeratedHistogram(name, action, SyncErrorPromptAction.NUM_ENTRIES);
    }

    @VisibleForTesting
    public static void setMessageDispatcherForTesting(MessageDispatcher dispatcherForTesting) {
        sMessageDispatcherForTesting = dispatcherForTesting;
    }

    @VisibleForTesting
    public static UnownedUserDataKey<SyncErrorMessage> getKeyForTesting() {
        return SYNC_ERROR_MESSAGE_KEY;
    }
}
