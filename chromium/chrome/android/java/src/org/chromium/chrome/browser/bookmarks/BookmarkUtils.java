// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.Browser;
import android.text.TextUtils;
import android.text.format.DateUtils;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.browser.customtabs.CustomTabsIntent;

import org.chromium.base.BuildInfo;
import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.IntentUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ActivityUtils;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.IntentHandler.IncognitoCCTCallerId;
import org.chromium.chrome.browser.LaunchIntentDispatcher;
import org.chromium.chrome.browser.app.bookmarks.BookmarkActivity;
import org.chromium.chrome.browser.app.bookmarks.BookmarkEditActivity;
import org.chromium.chrome.browser.app.bookmarks.BookmarkFolderSelectActivity;
import org.chromium.chrome.browser.browserservices.intents.BrowserServicesIntentDataProvider.CustomTabsUiType;
import org.chromium.chrome.browser.commerce.ShoppingFeatures;
import org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider;
import org.chromium.chrome.browser.customtabs.IncognitoCustomTabIntentDataProvider;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.feature_engagement.TrackerFactory;
import org.chromium.chrome.browser.preferences.ChromePreferenceKeys;
import org.chromium.chrome.browser.preferences.SharedPreferencesManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.read_later.ReadingListUtils;
import org.chromium.chrome.browser.renderer_host.ChromeNavigationUIData;
import org.chromium.chrome.browser.subscriptions.CommerceSubscriptionsServiceFactory;
import org.chromium.chrome.browser.subscriptions.SubscriptionsManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.AsyncTabCreationParams;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.ui.messages.snackbar.Snackbar;
import org.chromium.chrome.browser.ui.messages.snackbar.SnackbarManager;
import org.chromium.chrome.browser.ui.messages.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.user_education.UserEducationHelper;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkItem;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.components.browser_ui.bottomsheet.BottomSheetController;
import org.chromium.components.embedder_support.util.UrlConstants;
import org.chromium.components.feature_engagement.EventConstants;
import org.chromium.components.profile_metrics.BrowserProfileType;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.PageTransition;
import org.chromium.url.GURL;

import java.util.ArrayList;
import java.util.List;

// Vivaldi
import org.chromium.chrome.browser.app.ChromeActivity;
import org.chromium.chrome.browser.ChromeApplicationImpl;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.tab.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.widget.Toast;

import org.vivaldi.browser.common.VivaldiBookmarkUtils;
import org.vivaldi.browser.panels.PanelUtils;

import java.util.Locale;

/** A class holding static util functions for bookmark. */
public class BookmarkUtils {
    private static final String TAG = "BookmarkUtils";
    /** ID used to indicate an invalid bookmark node. */
    private static final long INVALID_BOOKMARK_ID = -1;

    /**
     * If the tab has already been bookmarked, start {@link BookmarkEditActivity} for the
     * normal bookmark or show the reading list page for reading list bookmark.
     * If not, add the bookmark to {@link BookmarkModel}, and show a snackbar notifying the user.
     *
     * @param existingBookmarkItem The {@link BookmarkItem} if the tab has already been bookmarked.
     * @param bookmarkModel The bookmark model.
     * @param tab The tab to add or edit a bookmark.
     * @param snackbarManager The {@link SnackbarManager} used to show the snackbar.
     * @param bottomSheetController The {@link BottomSheetController} used to show the bottom sheet.
     * @param activity Current activity.
     * @param fromCustomTab boolean indicates whether it is called by Custom Tab.
     * @param bookmarkType Type of the added bookmark.
     * @param callback Invoked with the resulting bookmark ID, which could be null if unsuccessful.
     * @param fromExplicitTrackUi Whether the bookmark was added directly from a tracking ui (e.g.
     *         the shopping "track price" button).
     */
    public static void addOrEditBookmark(@Nullable BookmarkItem existingBookmarkItem,
            BookmarkModel bookmarkModel, Tab tab, SnackbarManager snackbarManager,
            BottomSheetController bottomSheetController, Activity activity, boolean fromCustomTab,
            @BookmarkType int bookmarkType, Callback<BookmarkId> callback,
            boolean fromExplicitTrackUi) {
        assert bookmarkModel.isBookmarkModelLoaded();
        if (existingBookmarkItem != null) {
            startEditActivity(activity, existingBookmarkItem.getId());
            callback.onResult(existingBookmarkItem.getId());
            return;
        }

        // TODO(crbug.com/1252228): Reading list support needs some tests.
        if (BookmarkFeatures.isImprovedSaveFlowEnabled()) {
            BookmarkId newBookmarkId = addBookmarkInternal(activity, bookmarkModel, tab.getTitle(),
                    tab.getOriginalUrl(),
                    fromExplicitTrackUi ? bookmarkModel.getMobileFolderId() : null, bookmarkType);
            showSaveFlow(activity, bottomSheetController, fromExplicitTrackUi, newBookmarkId,
                    /*wasBookmarkMoved=*/false);
            callback.onResult(newBookmarkId);
            return;
        }

        BookmarkId newBookmarkId = addBookmarkAndShowSnackbar(
                bookmarkModel, tab, snackbarManager, activity, fromCustomTab, bookmarkType);
        callback.onResult(newBookmarkId);
    }

    /**
     * Shows the bookmark save flow.
     *
     * @param activity The current Activity.
     * @param bottomSheetController The BottomsheetController, used to show the save flow.
     * @param fromExplicitTrackUi Whether the bookmark was added from the explicit UI.
     * @param bookmarkId The BookmarkId to show the save flow for. Can be null in some cases.
     * @param wasBookmarkMoved Whether the save flow is shown as a reslult of a moved bookmark.
     */
    public static void showSaveFlow(@NonNull Activity activity,
            @NonNull BottomSheetController bottomSheetController, boolean fromExplicitTrackUi,
            @Nullable BookmarkId bookmarkId, boolean wasBookmarkMoved) {
        if (bookmarkId == null) {
            Log.e(TAG, "Null bookmark found when showing the save flow, aborting.");
            return;
        }

        SubscriptionsManager subscriptionService = null;
        if (ShoppingFeatures.isShoppingListEnabled()) {
            subscriptionService = new CommerceSubscriptionsServiceFactory()
                                          .getForLastUsedProfile()
                                          .getSubscriptionsManager();
        }

        BookmarkSaveFlowCoordinator bookmarkSaveFlowCoordinator =
                new BookmarkSaveFlowCoordinator(activity, bottomSheetController,
                        subscriptionService, new UserEducationHelper(activity, new Handler()));
        bookmarkSaveFlowCoordinator.show(bookmarkId, fromExplicitTrackUi, wasBookmarkMoved);
    }

    // The legacy code path to add or edit bookmark without triggering the bookmark bottom sheet.
    private static BookmarkId addBookmarkAndShowSnackbar(BookmarkModel bookmarkModel, Tab tab,
            SnackbarManager snackbarManager, Activity activity, boolean fromCustomTab,
            @BookmarkType int bookmarkType) {
        if (bookmarkType == BookmarkType.READING_LIST) {
            return addToReadingList(
                    tab.getOriginalUrl(), tab.getTitle(), snackbarManager, bookmarkModel, activity);
        }
        BookmarkId bookmarkId = addBookmarkInternal(activity, bookmarkModel, tab.getTitle(),
                tab.getOriginalUrl(), /*parent=*/null, BookmarkType.NORMAL);

        if (bookmarkId != null && bookmarkId.getType() == BookmarkType.NORMAL) {
            @BrowserProfileType
            int type = Profile.getBrowserProfileTypeFromProfile(
                    Profile.fromWebContents(tab.getWebContents()));
            RecordHistogram.recordEnumeratedHistogram(
                    "Bookmarks.AddedPerProfileType", type, BrowserProfileType.MAX_VALUE + 1);
        }

        Snackbar snackbar = null;
        if (bookmarkId == null) {
            snackbar = Snackbar.make(activity.getString(R.string.bookmark_page_failed),
                    new SnackbarController() {
                        @Override
                        public void onDismissNoAction(Object actionData) { }

                        @Override
                        public void onAction(Object actionData) { }
                    }, Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_BOOKMARK_ADDED)
                    .setSingleLine(false);
            RecordUserAction.record("EnhancedBookmarks.AddingFailed");
        } else {
            String folderName = bookmarkModel.getBookmarkTitle(
                    bookmarkModel.getBookmarkById(bookmarkId).getParentId());
            SnackbarController snackbarController =
                    createSnackbarControllerForEditButton(activity, bookmarkId);
            if (getLastUsedParent(activity, bookmarkModel) == null) {
                if (fromCustomTab) {
                    String packageLabel = BuildInfo.getInstance().hostPackageLabel;
                    snackbar = Snackbar.make(
                            activity.getString(R.string.bookmark_page_saved, packageLabel),
                            snackbarController, Snackbar.TYPE_ACTION, Snackbar.UMA_BOOKMARK_ADDED);
                } else {
                    snackbar = Snackbar.make(
                            activity.getString(R.string.bookmark_page_saved_default),
                            snackbarController, Snackbar.TYPE_ACTION, Snackbar.UMA_BOOKMARK_ADDED);
                }
            } else {
                snackbar = Snackbar.make(folderName, snackbarController, Snackbar.TYPE_ACTION,
                        Snackbar.UMA_BOOKMARK_ADDED)
                        .setTemplateText(activity.getString(R.string.bookmark_page_saved_folder));
            }
            if (ChromeApplicationImpl.isVivaldi())
                snackbar.setSingleLine(false).setAction(
                        activity.getString(R.string.bookmark_item_edit)
                                .toUpperCase(Locale.getDefault()), null)
                .setProfileImage(activity.getResources().getDrawable(R.drawable.ic_bookmark_small));
            else
            snackbar.setSingleLine(false).setAction(activity.getString(R.string.bookmark_item_edit),
                    null);
        }
        snackbarManager.showSnackbar(snackbar);
        return bookmarkId;
    }

    /**
     * Add an article to the reading list. If the article was already loaded, the entry will be
     * overwritten. After successful addition, a snackbar will be shown notifying the user about the
     * result of the operation.
     *
     * @param url The associated URL.
     * @param title The title of the reading list item being added.
     * @param snackbarManager The snackbar manager that will be used to show a snackbar.
     * @param bookmarkBridge The bookmark bridge that talks to the bookmark backend.
     * @param context The associated context.
     * @return The bookmark ID created after saving the article to the reading list.
     */
    public static BookmarkId addToReadingList(GURL url, String title,
            SnackbarManager snackbarManager, BookmarkBridge bookmarkBridge, Context context) {
        assert bookmarkBridge.isBookmarkModelLoaded();
        BookmarkId bookmarkId = bookmarkBridge.addToReadingList(title, url);

        if (bookmarkId != null) {
            Snackbar snackbar = Snackbar.make(context.getString(R.string.reading_list_saved),
                    new SnackbarController() {}, Snackbar.TYPE_ACTION,
                    Snackbar.UMA_READING_LIST_BOOKMARK_ADDED);
            snackbarManager.showSnackbar(snackbar);

            TrackerFactory.getTrackerForProfile(Profile.getLastUsedRegularProfile())
                    .notifyEvent(EventConstants.READ_LATER_ARTICLE_SAVED);
        }
        // Vivaldi - Display adding reading list result toast
        if (ChromeApplicationImpl.isVivaldi() && context instanceof ChromeTabbedActivity) {
            int message = bookmarkId == null
                    ? R.string.add_page_to_reading_list_error
                    : R.string.add_page_to_reading_list_confirm;
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
        return bookmarkId;
    }

    /**
     * Adds a bookmark with the given {@link Tab}. This will reset last used parent if it fails to
     * add a bookmark.
     *
     * @param context The current Android {@link Context}.
     * @param bookmarkModel The current {@link BookmarkModel} which talks to native.
     * @param bookmarkType The {@link BookmarkType} of the bookmark.
     */
    static BookmarkId addBookmarkInternal(Context context, BookmarkModel bookmarkModel,
            String title, GURL url, @Nullable BookmarkId parent, @BookmarkType int bookmarkType) {
        parent = parent == null ? getLastUsedParent(context, bookmarkModel) : parent;
        BookmarkItem parentItem = null;
        if (parent != null) {
            parentItem = bookmarkModel.getBookmarkById(parent);
        }
        if (parent == null || parentItem == null || parentItem.isManaged()
                || !parentItem.isFolder()) {
            parent = bookmarkModel.getDefaultFolder();
        }

        // Reading list items will be added when either one of the 2 conditions is met:
        // 1. The bookmark type explicitly specifies READING_LIST.
        // 2. The last used parent implicitly specifies READING_LIST.
        if (bookmarkType == BookmarkType.READING_LIST
                || parent.getType() == BookmarkType.READING_LIST) {
            return bookmarkModel.addToReadingList(title, url);
        }

        BookmarkId bookmarkId = null;
        // Use "New tab" as title for both incognito and regular NTP.
        if (url.getSpec().equals(UrlConstants.NTP_URL)) {
            title = context.getResources().getString(R.string.new_tab_title);
        }

        bookmarkId =
                bookmarkModel.addBookmark(parent, bookmarkModel.getChildCount(parent), title, url);
        // TODO(lazzzis): remove log after bookmark sync is fixed, crbug.com/986978
        if (bookmarkId == null) {
            Log.e(TAG,
                    "Failed to add bookmarks: parentTypeAndId %s, defaultFolderTypeAndId %s, "
                            + "mobileFolderTypeAndId %s, parentEditable Managed isFolder %s,",
                    parent, bookmarkModel.getDefaultFolder(), bookmarkModel.getMobileFolderId(),
                    parentItem == null ? "null"
                                       : (parentItem.isEditable() + " " + parentItem.isManaged()
                                               + " " + parentItem.isFolder()));
            setLastUsedParent(context, bookmarkModel.getDefaultFolder());
        }
        return bookmarkId;
    }

    /**
     * Creates a snackbar controller for a case where "Edit" button is shown to edit the newly
     * created bookmark.
     */
    private static SnackbarController createSnackbarControllerForEditButton(
            final Activity activity, final BookmarkId bookmarkId) {
        return new SnackbarController() {
            @Override
            public void onDismissNoAction(Object actionData) {
                RecordUserAction.record("EnhancedBookmarks.EditAfterCreateButtonNotClicked");
            }

            @Override
            public void onAction(Object actionData) {
                RecordUserAction.record("EnhancedBookmarks.EditAfterCreateButtonClicked");
                startEditActivity(activity, bookmarkId);
            }
        };
    }

    /**
     * Shows bookmark main UI.
     *
     * @param activity An activity to start the manager with.
     * @param isIncognito Whether the bookmark manager is opened in incognito mode.
     */
    public static void showBookmarkManager(Activity activity, boolean isIncognito) {
        showBookmarkManager(activity, null, isIncognito);
    }

    /**
     * Shows bookmark main UI.
     * @param activity An activity to start the manager with. If null, the bookmark manager will be
     *         started as a new task.
     * @param folderId The bookmark folder to open. If null, the bookmark manager will open the most
     *         recent folder.
     * @param isIncognito Whether the bookmark UI is opened in incognito mode.
     */
    public static void showBookmarkManager(
            @Nullable Activity activity, @Nullable BookmarkId folderId, boolean isIncognito) {
        ThreadUtils.assertOnUiThread();
        Context context = activity == null ? ContextUtils.getApplicationContext() : activity;
        String url = getFirstUrlToLoad(context, folderId);
        if (ChromeApplicationImpl.isVivaldi()) {
            PanelUtils.showPanel((ChromeActivity) activity, url, false);
            return;
        }

        if (ReadingListFeatures.shouldUseRootFolderAsDefaultForReadLater()
                && SharedPreferencesManager.getInstance().contains(
                        ChromePreferenceKeys.BOOKMARKS_LAST_USED_URL)) {
            RecordUserAction.record("MobileBookmarkManagerReopenBookmarksInSameSession");
        }

        // Tablet.
        if (DeviceFormFactor.isNonMultiDisplayContextOnTablet(context)) {
            openUrl(context, activity == null ? null : activity.getComponentName(), url, folderId,
                    isIncognito, /*launchType=*/null, /*openInNewTab=*/false);
            return;
        }

        // Phone.
        Intent intent = new Intent(context, BookmarkActivity.class);
        intent.putExtra(IntentHandler.EXTRA_INCOGNITO_MODE, isIncognito);
        intent.setData(Uri.parse(url));
        if (activity != null) {
            // Start from an existing activity.
            intent.putExtra(IntentHandler.EXTRA_PARENT_COMPONENT, activity.getComponentName());
            activity.startActivity(intent);
        } else {
            // Start a new task.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            IntentHandler.startActivityForTrustedIntent(intent);
        }
    }

    /**
     * @return the bookmark folder URL to open.
     */
    private static String getFirstUrlToLoad(Context context, @Nullable BookmarkId folderId) {
        String url;
        if (folderId == null) {
            // Load most recently visited bookmark folder.
            url = getLastUsedUrl(context);
        } else {
            // Load a specific folder.
            url = BookmarkUIState.createFolderUrl(folderId).toString();
        }

        return TextUtils.isEmpty(url) ? UrlConstants.BOOKMARKS_URL : url;
    }

    /**
     * Saves the last used url to preference. The saved url will be later queried by
     * {@link #getLastUsedUrl(Context)}
     */
    static void setLastUsedUrl(Context context, String url) {
        SharedPreferencesManager.getInstance().writeString(
                ChromePreferenceKeys.BOOKMARKS_LAST_USED_URL, url);
    }

    /**
     * Fetches url representing the user's state last time they close the bookmark manager.
     */
    @VisibleForTesting
    public static String getLastUsedUrl(Context context) {
        return SharedPreferencesManager.getInstance().readString(
                ChromePreferenceKeys.BOOKMARKS_LAST_USED_URL, UrlConstants.BOOKMARKS_URL);
    }

    /**
     * Save the last used {@link BookmarkId} as a folder to put new bookmarks to.
     */
    public static void setLastUsedParent(Context context, BookmarkId bookmarkId) {
        SharedPreferencesManager.getInstance().writeString(
                ChromePreferenceKeys.BOOKMARKS_LAST_USED_PARENT, bookmarkId.toString());
    }

    /**
     * @param context The current android {@link Context}.
     * @param bookmarkModel The bookmark model used to reset the last used parent for type swapping
     *         edge cases.
     * @return The parent {@link BookmarkId} that the user used the last time or null if the user
     *         has never selected a parent folder to use.
     */
    static BookmarkId getLastUsedParent(Context context, BookmarkModel bookmarkModel) {
        SharedPreferencesManager preferences = SharedPreferencesManager.getInstance();
        if (!preferences.contains(ChromePreferenceKeys.BOOKMARKS_LAST_USED_PARENT)) return null;

        BookmarkId parent = BookmarkId.getBookmarkIdFromString(
                preferences.readString(ChromePreferenceKeys.BOOKMARKS_LAST_USED_PARENT, null));

        // Vivaldi: bookmarkModel is null (and unneeded) when called from getLastUsedParentPublic.
        if (bookmarkModel != null) {
        // We need to reset the last used parent to support toggling reading list type-swapping.
        if (parent.getType() == BookmarkType.READING_LIST
                // Vivaldi - Reset last used parent to avoid next bookmarks being added as reading
                // list items
                && (!ReadingListFeatures.shouldAllowBookmarkTypeSwapping()
                || ChromeApplicationImpl.isVivaldi())) {
            setLastUsedParent(context, bookmarkModel.getDefaultFolder());
            return null;
        }
        } // Vivaldi
        return parent;
    }

    /** Starts an {@link BookmarkEditActivity} for the given {@link BookmarkId}. */
    public static void startEditActivity(Context context, BookmarkId bookmarkId) {
        RecordUserAction.record("MobileBookmarkManagerEditBookmark");
        if (ChromeApplicationImpl.isVivaldi()) {
            VivaldiBookmarkUtils.startEditActivity(context, bookmarkId, false);
            return;
        }
        Intent intent = new Intent(context, BookmarkEditActivity.class);
        intent.putExtra(BookmarkEditActivity.INTENT_BOOKMARK_ID, bookmarkId.toString());
        if (context instanceof BookmarkActivity) {
            ((BookmarkActivity) context).startActivityForResult(
                    intent, BookmarkActivity.EDIT_BOOKMARK_REQUEST_CODE);
        } else {
            context.startActivity(intent);
        }
    }

    /** Starts an {@link BookmarkFolderSelectActivity} for the given {@link BookmarkId}. */
    public static void startFolderSelectActivity(Context context, BookmarkId bookmarkId) {
        BookmarkFolderSelectActivity.startFolderSelectActivity(context, bookmarkId);
    }

    /**
     * Opens a bookmark and reports UMA.
     *
     * @param context The current context used to launch the intent.
     * @param componentName The component to use when opening a bookmark.
     * @param model Bookmarks model to manage the bookmark.
     * @param bookmarkId ID of the bookmark to be opened.
     * @param isIncognito Whether the bookmark manager is opened in incognito mode.
     * @param launchType The {@link TabLaunchType} to use for this bookmark, may be null.
     * @param openInNewTab Whether the bookmark should be opened in a new tab.
     * @return Whether the bookmark was successfully opened.
     */
    public static boolean openBookmark(Context context, ComponentName componentName,
            BookmarkModel model, @Nullable BookmarkId bookmarkId, boolean isIncognito,
            @Nullable @TabLaunchType Integer launchType, boolean openInNewTab) {
        if (model.getBookmarkById(bookmarkId) == null) return false;

        RecordUserAction.record("MobileBookmarkManagerEntryOpened");
        RecordHistogram.recordEnumeratedHistogram(
                "Bookmarks.OpenBookmarkType", bookmarkId.getType(), BookmarkType.LAST + 1);

        BookmarkItem bookmarkItem = model.getBookmarkById(bookmarkId);
        assert bookmarkItem != null;
        RecordHistogram.recordCustomTimesHistogram("Bookmarks.OpenBookmarkTimeInterval2."
                        + bookmarkTypeToHistogramSuffix(bookmarkId.getType()),
                System.currentTimeMillis() - bookmarkItem.getDateAdded(), 1,
                DateUtils.DAY_IN_MILLIS * 30, 50);

        if (bookmarkItem.getId().getType() == BookmarkType.READING_LIST
                && !bookmarkItem.isFolder()) {
            if (ChromeApplicationImpl.isVivaldi()) {
                new TabDelegate(isIncognito).createNewTab(
                        new LoadUrlParams(
                                bookmarkItem.getUrl()), TabLaunchType.FROM_BROWSER_ACTIONS, null);
            } else
            openReadingListItem(context, componentName, bookmarkItem.getUrl().getSpec(),
                    bookmarkItem.getId(), isIncognito, openInNewTab);
            model.setReadStatusForReadingList(bookmarkItem.getUrl(), true);
            // Vivaldi - Close Reading list fragment
            if (ChromeApplicationImpl.isVivaldi() && context instanceof ChromeTabbedActivity)
                ((ChromeTabbedActivity)context).getSupportFragmentManager().popBackStack();
        } else {
            // Note(david@vivaldi.com): Always use the parent component.
            if (ChromeApplicationImpl.isVivaldi())
                componentName = IntentUtils.safeGetParcelableExtra(
                         ((Activity) context).getIntent(), IntentHandler.EXTRA_PARENT_COMPONENT);
            openUrl(context, componentName, bookmarkItem.getUrl().getSpec(), bookmarkId,
                    isIncognito, launchType, openInNewTab);
        }
        return true;
    }

    private static String bookmarkTypeToHistogramSuffix(@BookmarkType int type) {
        switch (type) {
            case BookmarkType.NORMAL:
                return "Normal";
            case BookmarkType.PARTNER:
                return "Partner";
            case BookmarkType.READING_LIST:
                return "ReadingList";
        }
        assert false : "Unknown BookmarkType";
        return "";
    }

    /**
     * @param context {@link Context} used to retrieve the drawable.
     * @param type The bookmark type of the folder.
     * @return A {@link Drawable} to use for displaying bookmark folders.
     */
    public static Drawable getFolderIcon(Context context, @BookmarkType int type) {
        if (type == BookmarkType.READING_LIST) {
            return UiUtils.getTintedDrawable(
                    context, R.drawable.ic_reading_list_folder_24dp, getFolderIconTint(type));
        }
        return UiUtils.getTintedDrawable(
                context, R.drawable.ic_folder_blue_24dp, getFolderIconTint(type));
    }

    /**
     * @param type The bookmark type.
     * @return The tint used on the bookmark folder icon.
     */
    public static @ColorRes int getFolderIconTint(@BookmarkType int type) {
        return (type == BookmarkType.READING_LIST) ? R.color.default_icon_color_accent1_tint_list
                                                   : R.color.default_icon_color_tint_list;
    }

    /**
     * Retrieve the save flow start icon for the given bookmark.
     *
     * @param bookmarkId The {@link BookmarkId} to get the start icon for.
     * @return The start icon associated with the given bookmarkId.
     */
    public static Drawable getSaveFlowStartIconForBookmark(BookmarkId bookmarkId) {
        // TODO(crbug.com/1243383): Add start icon for price tracking.
        return null;
    }

    /**
     * Opens a url.
     *
     * @param context The Android context to use.
     * @param componentName The component to use when opening a bookmark.
     * @param url The url to open.
     * @param id The bookmarkId to open, can be null.
     * @param launchType If not null, url is opened in a new tab with the specified {@link
     *         TabLaunchType}.
     * @param isOffTheRecord Whether the url should be opened in incognito.
     * @param openInNewTab Whether the url should be opened in a new tab.
     */
    private static void openUrl(Context context, ComponentName componentName, String url,
            @Nullable BookmarkId id, boolean isIncognito,
            @Nullable @TabLaunchType Integer launchType, boolean openInNewTab) {
        if (openInNewTab) {
            openUrlInNewTab(context, componentName, url, id, isIncognito, launchType);
        } else {
            openUrlInCurrentTab(context, componentName, url, id, isIncognito, launchType);
        }
    }

    /** The same as {@link openUrl} - opens the url in the current tab, clobbering the contents. */
    private static void openUrlInCurrentTab(Context context, ComponentName componentName,
            String url, @Nullable BookmarkId id, boolean isIncognito,
            @Nullable @TabLaunchType Integer launchType) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.putExtra(
                Browser.EXTRA_APPLICATION_ID, context.getApplicationContext().getPackageName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(IntentHandler.EXTRA_PAGE_TRANSITION_TYPE, PageTransition.AUTO_BOOKMARK);
        if (id != null) {
            intent.putExtra(IntentHandler.EXTRA_PAGE_TRANSITION_BOOKMARK_ID, id.toString());
        }

        if (launchType != null) {
            IntentHandler.setTabLaunchType(intent, launchType);
            if (isIncognito) {
                intent.putExtra(IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, true);
            } else {
                intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
            }
        } else {
            intent.putExtra(IntentHandler.EXTRA_INCOGNITO_MODE, isIncognito);
        }

        if (componentName != null) {
            ActivityUtils.setNonAliasedComponentForMainBrowsingActivity(intent, componentName);
        } else {
            // If the bookmark manager is shown in a tab on a phone (rather than in a separate
            // activity) the component name may be null. Send the intent through
            // ChromeLauncherActivity instead to avoid crashing. See crbug.com/615012.
            intent.setClass(context.getApplicationContext(), ChromeLauncherActivity.class);
        }

        IntentHandler.startActivityForTrustedIntent(intent);
    }

    /** Same as {@link openUrl} - opens the url in a new tab. */
    private static void openUrlInNewTab(Context context, ComponentName componentName, String url,
            @Nullable BookmarkId id, boolean isIncognito,
            @Nullable @TabLaunchType Integer launchType) {
        TabDelegate tabDelegate = new TabDelegate(isIncognito);
        LoadUrlParams params = new LoadUrlParams(url, PageTransition.AUTO_BOOKMARK);
        ChromeNavigationUIData navData = new ChromeNavigationUIData();
        navData.setBookmarkId(
                id.getType() == BookmarkType.NORMAL ? id.getId() : INVALID_BOOKMARK_ID);
        params.setNavigationUIDataSupplier(navData::createUnownedNativeCopy);
        AsyncTabCreationParams asyncParams = new AsyncTabCreationParams(params, componentName);
        tabDelegate.createNewTab(asyncParams, launchType, Tab.INVALID_TAB_ID);
    }

    private static void openReadingListItem(Context context, ComponentName componentName,
            String url, BookmarkId id, boolean isIncognito, boolean openInNewTab) {
        if (ReadingListFeatures.shouldUseCustomTab()) {
            openReadingListInCustomTab(context, url, isIncognito);
        } else {
            openUrl(context, componentName, url, id, isIncognito, TabLaunchType.FROM_READING_LIST,
                    openInNewTab);
        }
    }

    private static void openReadingListInCustomTab(
            Context context, String url, boolean isIncognito) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setShowTitle(true);
        builder.setShareState(CustomTabsIntent.SHARE_STATE_ON);
        CustomTabsIntent customTabIntent = builder.build();
        customTabIntent.intent.setData(Uri.parse(url));

        Intent intent = LaunchIntentDispatcher.createCustomTabActivityIntent(
                context, customTabIntent.intent);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        intent.putExtra(CustomTabIntentDataProvider.EXTRA_UI_TYPE, CustomTabsUiType.READ_LATER);

        // Extras for incognito CCT.
        if (isIncognito) {
            IncognitoCustomTabIntentDataProvider.addIncognitoExtrasForChromeFeatures(
                    intent, IncognitoCCTCallerId.READ_LATER);
        }

        IntentUtils.addTrustedIntentExtras(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        IntentHandler.startActivityForTrustedIntent(intent);
    }

    /**
     * Closes the {@link BookmarkActivity} on Phone. Does nothing on tablet.
     */
    public static void finishActivityOnPhone(Context context) {
        if (context instanceof BookmarkActivity) {
            ((Activity) context).finish();
        }
    }

    /**
     * Populates the top level bookmark folder ids.
     *
     * @param bookmarkModel The bookmark model that talks to bookmark native backend.
     * @return The list of top level bookmark folder ids.
     */
    public static List<BookmarkId> populateTopLevelFolders(BookmarkModel bookmarkModel) {
        List<BookmarkId> topLevelFolders = new ArrayList<>();
        BookmarkId desktopNodeId = bookmarkModel.getDesktopFolderId();
        BookmarkId mobileNodeId = bookmarkModel.getMobileFolderId();
        BookmarkId othersNodeId = bookmarkModel.getOtherFolderId();

        List<BookmarkId> specialFoldersIds =
                bookmarkModel.getTopLevelFolderIDs(/*getSpecial=*/true, /*getNormal=*/false);
        BookmarkId rootFolder = bookmarkModel.getRootFolderId();

        // managed and partner bookmark folders will be put to the bottom.
        List<BookmarkId> managedAndPartnerFolderIds = new ArrayList<>();

        for (BookmarkId bookmarkId : specialFoldersIds) {
            // Adds reading list as the first top level folder.
            if (bookmarkId.getType() == BookmarkType.READING_LIST) {
                topLevelFolders.add(bookmarkId);
                TrackerFactory.getTrackerForProfile(Profile.getLastUsedRegularProfile())
                        .notifyEvent(EventConstants.READ_LATER_BOTTOM_SHEET_FOLDER_SEEN);
                continue;
            }
            BookmarkId parent = bookmarkModel.getBookmarkById(bookmarkId).getParentId();
            if (parent.equals(rootFolder)) managedAndPartnerFolderIds.add(bookmarkId);
        }

        // Adds normal bookmark top level folders.
        if (bookmarkModel.isFolderVisible(mobileNodeId)) {
            topLevelFolders.add(mobileNodeId);
        }
        if (bookmarkModel.isFolderVisible(desktopNodeId)) {
            topLevelFolders.add(desktopNodeId);
        }
        if (bookmarkModel.isFolderVisible(othersNodeId)) {
            topLevelFolders.add(othersNodeId);
        }

        if (ChromeApplicationImpl.isVivaldi()) {
            BookmarkId trashNodeId = bookmarkModel.getTrashFolderId();
            if (bookmarkModel.isFolderVisible(trashNodeId)) {
                topLevelFolders.add(trashNodeId);
            }

        }

        // Add any top-level managed and partner bookmark folders that are children of the root
        // folder.
        topLevelFolders.addAll(managedAndPartnerFolderIds);
        return topLevelFolders;
    }

    /**
     * Expires the stored last used url if Chrome has been in the background long enough to mark it
     * as a new session. We're using the "Start Surface" concept of session here which is if the
     * app has been in the background for X amount of time. Called from #onStartWithNative, after
     * which the time stored in {@link ChromeInactivityTracker} is expired.
     *
     * @param timeSinceLastBackgroundedMs The time since Chrome has sent into the background.
     */
    public static void maybeExpireLastBookmarkLocationForReadLater(
            long timeSinceLastBackgroundedMs) {
        if (!ReadingListFeatures.shouldUseRootFolderAsDefaultForReadLater()) return;

        int readLaterSessionLengthMs = ReadingListFeatures.getSessionLengthMs();
        if (timeSinceLastBackgroundedMs > readLaterSessionLengthMs) {
            SharedPreferencesManager.getInstance().removeKey(
                    ChromePreferenceKeys.BOOKMARKS_LAST_USED_URL);
        }
    }

    /** Returns whether this bookmark can be moved */
    public static boolean isMovable(BookmarkItem node) {
        return ReadingListUtils.isSwappableReadingListItem(node.getId()) || node.isReorderable();
    }

    /** Allows strings to be landed for translation. */
    private void fakeFunctiontoAllowStringMerge() {
        int id = R.string.price_tracking_save_flow_notification_switch_subtitle_error;
        id = R.string.disable_price_tracking_menu_item;
        id = R.string.price_tracking_bookmarks_filter_title;
        id = R.string.tracked_products_empty_list_title;
        id = R.string.price_tracking_disabled_snackbar;
        id = R.string.price_tracking_enabled_snackbar;
        id = R.string.price_tracking_error_snackbar;
        id = R.string.price_tracking_error_snackbar_action;
        id = R.string.iph_price_tracking_menu_item;
        id = R.string.iph_price_tracking_menu_item_accessibility;
        id = R.string.iph_shopping_list_save_flow;
        id = R.string.iph_shopping_list_save_flow_accessibility;
    }

    // Vivaldi
    public static BookmarkId getLastUsedParentPublic(Context context) {
        return getLastUsedParent(context, null);
    }

    public static void setLastUsedParentPublic(Context context, BookmarkId bookmarkId) {
        setLastUsedParent(context, bookmarkId);
    }

    public static String getFirstUrlToLoadPublic(Context context, BookmarkId folderId) {
        return getFirstUrlToLoad(context, folderId);
    }
}
