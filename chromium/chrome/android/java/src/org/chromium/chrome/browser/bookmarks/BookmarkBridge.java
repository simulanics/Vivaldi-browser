// Copyright 2013 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.common.primitives.UnsignedLongs;
import com.google.protobuf.InvalidProtocolBufferException;

import org.chromium.base.ContextUtils;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.NativeMethods;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.commerce.ShoppingFeatures;
import org.chromium.chrome.browser.partnerbookmarks.PartnerBookmarksShim;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.read_later.ReadingListUtils;
import org.chromium.chrome.browser.subscriptions.CommerceSubscription;
import org.chromium.chrome.browser.subscriptions.CommerceSubscriptionsServiceFactory;
import org.chromium.chrome.browser.subscriptions.SubscriptionsManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkItem;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.components.commerce.PriceTracking.ProductPrice;
import org.chromium.components.power_bookmarks.PowerBookmarkMeta;
import org.chromium.components.power_bookmarks.PowerBookmarkType;
import org.chromium.components.power_bookmarks.ShoppingSpecifics;
import org.chromium.content_public.browser.WebContents;
import org.chromium.url.GURL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.chromium.chrome.browser.ChromeApplicationImpl;

/**
 * Provides the communication channel for Android to fetch and manipulate the
 * bookmark model stored in native.
 */
class BookmarkBridge {
    private final Profile mProfile;
    private boolean mIsDestroyed;
    private boolean mIsDoingExtensiveChanges;
    private long mNativeBookmarkBridge;
    private boolean mIsNativeBookmarkModelLoaded;
    private final List<DelayedBookmarkCallback> mDelayedBookmarkCallbacks =
            new ArrayList<DelayedBookmarkCallback>();
    private final ObserverList<BookmarkModelObserver> mObservers =
            new ObserverList<BookmarkModelObserver>();
    private SubscriptionsManager mSubscriptionManager;
    private SubscriptionsManager.SubscriptionObserver mSubscriptionsObserver;

    /**
     * Interface for callback object for fetching bookmarks and folder hierarchy.
     */
    public interface BookmarksCallback {
        /**
         * Callback method for fetching bookmarks for a folder and the folder hierarchy.
         * @param folderId The folder id to which the bookmarks belong.
         * @param bookmarksList List holding the fetched bookmarks and details.
         */
        @CalledByNative("BookmarksCallback")
        void onBookmarksAvailable(BookmarkId folderId, List<BookmarkItem> bookmarksList);

        /**
         * Callback method for fetching the folder hierarchy.
         * @param folderId The folder id to which the bookmarks belong.
         * @param bookmarksList List holding the fetched folder details.
         */
        @CalledByNative("BookmarksCallback")
        void onBookmarksFolderHierarchyAvailable(BookmarkId folderId,
                List<BookmarkItem> bookmarksList);
    }

    /** A callback for updates to the price of a product. */
    public interface PriceUpdateCallback {
        /**
         * @param id The bookmark ID that the price information was fetched for.
         * @param url The URL of the product that was updated.
         * @param price The price of the product.
         */
        void onPriceUpdated(BookmarkId id, GURL url, ProductPrice price);
    }

    /**
     * Handler to fetch the bookmarks, titles, urls and folder hierarchy.
     * @param profile Profile instance corresponding to the active profile.
     */
    public BookmarkBridge(Profile profile) {
        ThreadUtils.assertOnUiThread();
        mProfile = profile;
        mNativeBookmarkBridge = BookmarkBridgeJni.get().init(BookmarkBridge.this, profile);
        mIsDoingExtensiveChanges = BookmarkBridgeJni.get().isDoingExtensiveChanges(
                mNativeBookmarkBridge, BookmarkBridge.this);
        mSubscriptionsObserver = new SubscriptionsManager.SubscriptionObserver() {
            @Override
            public void onSubscribe(List<CommerceSubscription> subscriptions) {}

            @Override
            public void onUnsubscribe(List<CommerceSubscription> subscriptions) {
                removeExplicitShoppingSubscriptions(subscriptions);
            }
        };
        if (ShoppingFeatures.isShoppingListEnabled()) {
            mSubscriptionManager = new CommerceSubscriptionsServiceFactory()
                                           .getForLastUsedProfile()
                                           .getSubscriptionsManager();
            mSubscriptionManager.addObserver(mSubscriptionsObserver);
        }
    }

    @VisibleForTesting
    SubscriptionsManager.SubscriptionObserver getSubscriptionObserver() {
        return mSubscriptionsObserver;
    }

    /**
     * Destroys this instance so no further calls can be executed.
     */
    public void destroy() {
        mIsDestroyed = true;
        if (mNativeBookmarkBridge != 0) {
            BookmarkBridgeJni.get().destroy(mNativeBookmarkBridge, BookmarkBridge.this);
            mNativeBookmarkBridge = 0;
            mIsNativeBookmarkModelLoaded = false;
            mDelayedBookmarkCallbacks.clear();
        }
        mObservers.clear();

        if (mSubscriptionManager != null) {
            mSubscriptionManager.removeObserver(mSubscriptionsObserver);
        }
    }

    /** Returns whether the bridge has been destroyed. */
    public boolean isDestroyed() {
        return mIsDestroyed;
    }

    /**
     * @param tab Tab whose current URL is checked against.
     * @return {@code true} if the current Tab URL has a bookmark associated with it. If the
     *         bookmark backend is not loaded, return {@code false}.
     */
    public boolean hasBookmarkIdForTab(@Nullable Tab tab) {
        ThreadUtils.assertOnUiThread();
        return getUserBookmarkIdForTab(tab) != null;
    }

    /**
     * @param tab Tab whose current URL is checked against.
     * @return BookmarkId or {@link null} if bookmark backend is not loaded or the tab is frozen.
     */
    @Nullable
    public BookmarkId getUserBookmarkIdForTab(@Nullable Tab tab) {
        ThreadUtils.assertOnUiThread();
        if (tab == null || tab.isFrozen() || mNativeBookmarkBridge == 0) return null;
        return BookmarkBridgeJni.get().getBookmarkIdForWebContents(
                mNativeBookmarkBridge, this, tab.getWebContents(), true);
    }

    /**
     * Load an empty partner bookmark shim for testing. The root node for bookmark will be an
     * empty node.
     */
    @VisibleForTesting
    public void loadEmptyPartnerBookmarkShimForTesting() {
        BookmarkBridgeJni.get().loadEmptyPartnerBookmarkShimForTesting(
                mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /**
     * Load a fake partner bookmark shim for testing. To see (or edit) the titles and URLs of the
     * partner bookmarks, go to bookmark_bridge.cc.
     */
    @VisibleForTesting
    public void loadFakePartnerBookmarkShimForTesting() {
        BookmarkBridgeJni.get().loadFakePartnerBookmarkShimForTesting(
                mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /**
     * Add an observer to bookmark model changes.
     * @param observer The observer to be added.
     */
    public void addObserver(BookmarkModelObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Remove an observer of bookmark model changes.
     * @param observer The observer to be removed.
     */
    public void removeObserver(BookmarkModelObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * @return Whether or not the underlying bookmark model is loaded.
     */
    public boolean isBookmarkModelLoaded() {
        return mIsNativeBookmarkModelLoaded;
    }

    /**
     * Schedules a runnable to run after the bookmark model is loaded. If the
     * model is already loaded, executes the runnable immediately. If not, also
     * kick off partner bookmark reading.
     * @return Whether the given runnable is executed synchronously.
     */
    public boolean finishLoadingBookmarkModel(final Runnable runAfterModelLoaded) {
        if (isBookmarkModelLoaded()) {
            runAfterModelLoaded.run();
            return true;
        }

        long startTime = SystemClock.elapsedRealtime();
        addObserver(new BookmarkModelObserver() {
            @Override
            public void bookmarkModelLoaded() {
                removeObserver(this);
                RecordHistogram.recordTimesHistogram(
                        "PartnerBookmark.LoadingTime", SystemClock.elapsedRealtime() - startTime);
                runAfterModelLoaded.run();
            }
            @Override
            public void bookmarkModelChanged() {
            }
        });

        // Start reading as a fail-safe measure to avoid waiting forever if the caller forgets to
        // call kickOffReading().
        PartnerBookmarksShim.kickOffReading(ContextUtils.getApplicationContext());
        return false;
    }

    /**
     * Gets the {@link BookmarkItem} which is referenced by the given {@link BookmarkId}.
     * @param id The {@link BookmarkId} used to lookup the corresponding {@link BookmarkItem}.
     * @return A BookmarkItem instance for the given BookmarkId.
     *         <code>null</code> if it doesn't exist.
     */
    @Nullable
    public BookmarkItem getBookmarkById(@Nullable BookmarkId id) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        if (id == null) return null;

        if (BookmarkId.SHOPPING_FOLDER.equals(id)) {
            return new BookmarkItem(id, /*title=*/null, /*url=*/null,
                    /*isFolder=*/true, /*parentId=*/getRootFolderId(), /*isEditable=*/false,
                    /*isManaged=*/false, /*dateAdded=*/0L, /*read=*/false);
        }

        return BookmarkBridgeJni.get().getBookmarkByID(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType());
    }

    /**
     * @return The top level folder's parents.
     */
    public List<BookmarkId> getTopLevelFolderParentIDs() {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        List<BookmarkId> result = new ArrayList<BookmarkId>();
        BookmarkBridgeJni.get().getTopLevelFolderParentIDs(
                mNativeBookmarkBridge, BookmarkBridge.this, result);
        return result;
    }

    /**
     * @param getSpecial Whether special top folders should be returned.
     * @param getNormal  Whether normal top folders should be returned.
     * @return The top level folders. Note that special folders come first and normal top folders
     *         will be in the alphabetical order.
     */
    public List<BookmarkId> getTopLevelFolderIDs(boolean getSpecial, boolean getNormal) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        List<BookmarkId> result = new ArrayList<BookmarkId>();
        BookmarkBridgeJni.get().getTopLevelFolderIDs(
                mNativeBookmarkBridge, BookmarkBridge.this, getSpecial, getNormal, result);
        return result;
    }

    /** Returns the synthetic reading list folder. */
    public BookmarkId getReadingListFolder() {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getReadingListFolder(
                mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /**
     * Populates folderList with BookmarkIds of folders users can move bookmarks
     * to and all folders have corresponding depth value in depthList. Folders
     * having depths of 0 will be shown as top-layered folders. These include
     * "Desktop Folder" itself as well as all children of "mobile" and "other".
     * Children of 0-depth folders have depth of 1, and so on.
     *
     * The result list will be sorted alphabetically by title. "mobile", "other",
     * root node, managed folder, partner folder are NOT included as results.
     */
    @VisibleForTesting
    public void getAllFoldersWithDepths(List<BookmarkId> folderList,
            List<Integer> depthList) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        BookmarkBridgeJni.get().getAllFoldersWithDepths(
                mNativeBookmarkBridge, BookmarkBridge.this, folderList, depthList);
    }

    /**
     * Calls {@link #getAllFoldersWithDepths(List, List)} and remove all folders and children
     * in bookmarksToMove. This method is useful when finding a list of possible parent folers when
     * moving some folders (a folder cannot be moved to its own children).
     */
    public void getMoveDestinations(List<BookmarkId> folderList,
            List<Integer> depthList, List<BookmarkId> bookmarksToMove) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        BookmarkBridgeJni.get().getAllFoldersWithDepths(
                mNativeBookmarkBridge, BookmarkBridge.this, folderList, depthList);
        if (bookmarksToMove == null || bookmarksToMove.size() == 0) return;

        boolean shouldTrim = false;
        int trimThreshold = -1;
        for (int i = 0; i < folderList.size(); i++) {
            int depth = depthList.get(i);
            if (shouldTrim) {
                if (depth <= trimThreshold) {
                    shouldTrim = false;
                    trimThreshold = -1;
                } else {
                    folderList.remove(i);
                    depthList.remove(i);
                    i--;
                }
            }
            // Do not use else here because shouldTrim could be set true after if (shouldTrim)
            // statement.
            if (!shouldTrim) {
                BookmarkId folder = folderList.get(i);
                if (bookmarksToMove.contains(folder)) {
                    shouldTrim = true;
                    trimThreshold = depth;
                    folderList.remove(i);
                    depthList.remove(i);
                    i--;
                }
            }
        }
    }

    /**
     * @return The BookmarkId for root folder node
     */
    public BookmarkId getRootFolderId() {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getRootFolderId(mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /**
     * @return The BookmarkId for Mobile folder node
     */
    public BookmarkId getMobileFolderId() {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getMobileFolderId(
                mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /**
     * @return Id representing the special "other" folder from bookmark model.
     */
    public BookmarkId getOtherFolderId() {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getOtherFolderId(mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /**
     * @return BookmarkId representing special "desktop" folder, namely "bookmark bar".
     */
    public BookmarkId getDesktopFolderId() {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getDesktopFolderId(
                mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /**
     * Gets Bookmark GUID which is immutable and differs from the BookmarkId in that it is
     * consistent across different clients and stable throughout the lifetime of the bookmark, with
     * the exception of nodes added to the Managed Bookmarks folder, whose GUIDs are re-assigned at
     * start-up every time.
     *
     * @return Bookmark GUID of the given node.
     */
    @VisibleForTesting
    public String getBookmarkGuidByIdForTesting(BookmarkId id) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getBookmarkGuidByIdForTesting(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType());
    }

    /**
     * @return The number of children that the given node has.
     */
    public int getChildCount(BookmarkId id) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getChildCount(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType());
    }

    /**
     * Reads sub-folder IDs, sub-bookmark IDs, or both of the given folder.
     *
     * @return Child IDs of the given folder, with the specified type.
     */
    public List<BookmarkId> getChildIDs(BookmarkId id) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        if (BookmarkId.SHOPPING_FOLDER.equals(id)) {
            return searchBookmarks("", null, PowerBookmarkType.SHOPPING, -1);
        }
        List<BookmarkId> result = new ArrayList<BookmarkId>();
        BookmarkBridgeJni.get().getChildIDs(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType(), result);
        return result;
    }

    public List<BookmarkId> getChildIDsVivaldi(BookmarkId id, boolean getFolders,
                                               boolean getBookmarks, boolean getSeparators) {
        // TODO(crbug.com/160194): Remove boolean parameters after bookmark reordering launches.
        assert mIsNativeBookmarkModelLoaded;
        List<BookmarkId> result = new ArrayList<BookmarkId>();
        BookmarkBridgeJni.get().getChildIDsVivaldi(mNativeBookmarkBridge, BookmarkBridge.this,
                id.getId(), id.getType(), getFolders, getBookmarks, getSeparators, result);
        return result;
    }

    /**
     * Disable price tracking for the list of subscriptions. This flips the bit in ShoppingSpecifics
     * but does not actually unsubscribe from the subscription service -- this assumes that is
     * already done.
     * TODO(1284730): This should live somewhere other than BookmarkBridge.
     *
     * @param subscriptions The list of subscriptions to disable.
     */
    private void removeExplicitShoppingSubscriptions(List<CommerceSubscription> subscriptions) {
        if (subscriptions == null) return;

        List<BookmarkId> products = searchBookmarks("", null, PowerBookmarkType.SHOPPING, -1);
        if (products == null || products.size() == 0) return;

        // Put the offer and cluster IDs into a map so they can be quickly checked.
        HashSet<Long> offerIdMap = new HashSet<>();
        HashSet<Long> clusterIdMap = new HashSet<>();
        for (CommerceSubscription c : subscriptions) {
            // Ensure the subscription is explicit.
            if (c == null
                    || !c.getManagementType().equals(
                            CommerceSubscription.SubscriptionManagementType.USER_MANAGED)) {
                continue;
            }

            if (c.getTrackingIdType().equals(CommerceSubscription.TrackingIdType.OFFER_ID)) {
                offerIdMap.add(UnsignedLongs.parseUnsignedLong(c.getTrackingId()));
            } else if (c.getTrackingIdType().equals(
                               CommerceSubscription.TrackingIdType.PRODUCT_CLUSTER_ID)) {
                clusterIdMap.add(UnsignedLongs.parseUnsignedLong(c.getTrackingId()));
            }
        }

        // Look at all the products the user has saved to find any of the above product or cluster
        // IDs.
        for (BookmarkId product : products) {
            PowerBookmarkMeta meta = getPowerBookmarkMeta(product);
            if (!meta.hasShoppingSpecifics()) continue;

            ShoppingSpecifics specifics = meta.getShoppingSpecifics();
            if (offerIdMap.contains(specifics.getOfferId())
                    || clusterIdMap.contains(specifics.getProductClusterId())) {
                // Reset the meta using a copy of the existing one, but set the price tracking flag
                // to false.
                setPowerBookmarkMeta(product,
                        PowerBookmarkMeta.newBuilder(meta)
                                .setShoppingSpecifics(
                                        ShoppingSpecifics.newBuilder(meta.getShoppingSpecifics())
                                                .setIsPriceTracked(false)
                                                .build())
                                .build());
            }
        }
    }

    /**
     * Gets the child of a folder at the specific position.
     * @param folderId Id of the parent folder
     * @param index Position of child among all children in folder
     * @return BookmarkId of the child, which will be null if folderId does not point to a folder or
     *         index is invalid.
     */
    public BookmarkId getChildAt(BookmarkId folderId, int index) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getChildAt(mNativeBookmarkBridge, BookmarkBridge.this,
                folderId.getId(), folderId.getType(), index);
    }

    /**
     * Get the total number of bookmarks in the sub tree of the specified folder.
     * @param id The {@link BookmarkId} of the folder to be queried.
     * @return The total number of bookmarks in the folder.
     */
    public int getTotalBookmarkCount(BookmarkId id) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getTotalBookmarkCount(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType());
    }

    /**
     * Synchronously gets a list of bookmarks that match the specified search query.
     * @param query Keyword used for searching bookmarks.
     * @param maxNumberOfResult Maximum number of result to fetch.
     * @return List of bookmark IDs that are related to the given query.
     */
    public List<BookmarkId> searchBookmarks(String query, int maxNumberOfResult) {
        return searchBookmarks(query, null, null, maxNumberOfResult);
    }

    /**
     * Synchronously gets a list of bookmarks that match the specified search query.
     * @param query Keyword used for searching bookmarks.
     * @param tags A list of tags the resulting bookmarks should have.
     * @param powerBookmarkType The type of power bookmark type to search for (or null for all).
     * @param maxNumberOfResult Maximum number of result to fetch.
     * @return List of bookmark IDs that are related to the given query.
     */
    public List<BookmarkId> searchBookmarks(String query, @Nullable String[] tags,
            @Nullable PowerBookmarkType powerBookmarkType, int maxNumberOfResult) {
        ThreadUtils.assertOnUiThread();
        List<BookmarkId> bookmarkMatches = new ArrayList<>();
        int typeInt = powerBookmarkType == null ? -1 : powerBookmarkType.getNumber();
        BookmarkBridgeJni.get().searchBookmarks(mNativeBookmarkBridge, BookmarkBridge.this,
                bookmarkMatches, query, tags, typeInt, maxNumberOfResult);
        return bookmarkMatches;
    }

    /**
     * Synchronously gets a list of bookmarks of the given type
     * @param powerBookmarkType The type of power bookmark type to search for (or null for all).
     * @return List of bookmark IDs that are related to the given query.
     */
    public List<BookmarkId> getBookmarksOfType(@NonNull PowerBookmarkType powerBookmarkType) {
        ThreadUtils.assertOnUiThread();
        List<BookmarkId> bookmarkMatches = new ArrayList<>();
        int typeInt = powerBookmarkType.getNumber();
        BookmarkBridgeJni.get().getBookmarksOfType(
                mNativeBookmarkBridge, BookmarkBridge.this, bookmarkMatches, typeInt);
        return bookmarkMatches;
    }

    /**
     * Set title of the given bookmark.
     */
    public void setBookmarkTitle(BookmarkId id, String title) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        BookmarkBridgeJni.get().setBookmarkTitle(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType(), title);
    }

    /**
     * Set URL of the given bookmark.
     */
    public void setBookmarkUrl(BookmarkId id, GURL url) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        assert id.getType() == BookmarkType.NORMAL;
        BookmarkBridgeJni.get().setBookmarkUrl(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType(), url);
    }

    /**
     * Retrieve the PowerBookmarkMeta for a node if it exists.
     * @param id The {@link BookmarkId} of the bookmark to fetch the meta for.
     * @return The meta or null if none exists.
     */
    public @Nullable PowerBookmarkMeta getPowerBookmarkMeta(@Nullable BookmarkId id) {
        if (id == null || mNativeBookmarkBridge == 0) return null;
        byte[] protoBytes = BookmarkBridgeJni.get().getPowerBookmarkMeta(
                mNativeBookmarkBridge, this, id.getId(), id.getType());

        if (protoBytes == null) return null;

        try {
            return PowerBookmarkMeta.parseFrom(protoBytes);
        } catch (InvalidProtocolBufferException ex) {
            deletePowerBookmarkMeta(id);
            return null;
        }
    }

    /**
     * Set the PowerBookmarkMeta for a node. This MUST be called in order to persist any changes
     * made to the proto in the java layer.
     * @param id The ID of the bookmark to set the meta on.
     * @param meta The meta to store.
     */
    public void setPowerBookmarkMeta(BookmarkId id, PowerBookmarkMeta meta) {
        if (meta == null) return;
        BookmarkBridgeJni.get().setPowerBookmarkMeta(mNativeBookmarkBridge, BookmarkBridge.this,
                id.getId(), id.getType(), meta.toByteArray());
    }

    /**
     * Delete the PowerBookmarkMeta from a node.
     * @param id The ID of the bookmark to remove the meta from.
     */
    public void deletePowerBookmarkMeta(BookmarkId id) {
        BookmarkBridgeJni.get().deletePowerBookmarkMeta(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType());
    }

    /**
     * @return Whether the given bookmark exist in the current bookmark model, e.g., not deleted.
     */
    public boolean doesBookmarkExist(BookmarkId id) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().doesBookmarkExist(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType());
    }

    /**
     * Fetches the bookmarks of the given folder. This is an always-synchronous version of another
     * getBookmarksForFolder function.
     *
     * @param folderId The parent folder id.
     * @return Bookmarks of the given folder.
     */
    public List<BookmarkItem> getBookmarksForFolder(BookmarkId folderId) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        List<BookmarkItem> result = new ArrayList<BookmarkItem>();
        BookmarkBridgeJni.get().getBookmarksForFolder(
                mNativeBookmarkBridge, BookmarkBridge.this, folderId, null, result);
        return result;
    }

    /**
     * Fetches the bookmarks of the current folder. Callback will be
     * synchronous if the bookmark model is already loaded and async if it is loaded in the
     * background.
     * @param folderId The current folder id.
     * @param callback Instance of a callback object.
     */
    public void getBookmarksForFolder(BookmarkId folderId, BookmarksCallback callback) {
        ThreadUtils.assertOnUiThread();
        if (mIsNativeBookmarkModelLoaded) {
            BookmarkBridgeJni.get().getBookmarksForFolder(mNativeBookmarkBridge,
                    BookmarkBridge.this, folderId, callback, new ArrayList<BookmarkItem>());
        } else {
            mDelayedBookmarkCallbacks.add(new DelayedBookmarkCallback(folderId, callback,
                    DelayedBookmarkCallback.GET_BOOKMARKS_FOR_FOLDER, this));
        }
    }

    /**
     * Check whether the given folder should be visible. This is for top permanent folders that we
     * want to hide when there is no child.
     * @return Whether the given folder should be visible.
     */
    public boolean isFolderVisible(BookmarkId id) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        if (ReadingListUtils.isSwappableReadingListItem(id)) {
            return true;
        }
        return BookmarkBridgeJni.get().isFolderVisible(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType());
    }

    /**
     * Fetches the folder hierarchy of the given folder. Callback will be
     * synchronous if the bookmark model is already loaded and async if it is loaded in the
     * background.
     * @param folderId The current folder id.
     * @param callback Instance of a callback object.
     */
    public void getCurrentFolderHierarchy(BookmarkId folderId, BookmarksCallback callback) {
        ThreadUtils.assertOnUiThread();
        if (mIsNativeBookmarkModelLoaded) {
            BookmarkBridgeJni.get().getCurrentFolderHierarchy(mNativeBookmarkBridge,
                    BookmarkBridge.this, folderId, callback, new ArrayList<BookmarkItem>());
        } else {
            mDelayedBookmarkCallbacks.add(new DelayedBookmarkCallback(folderId, callback,
                    DelayedBookmarkCallback.GET_CURRENT_FOLDER_HIERARCHY, this));
        }
    }

    /**
     * Deletes a specified bookmark node.
     * @param bookmarkId The ID of the bookmark to be deleted.
     */
    public void deleteBookmark(BookmarkId bookmarkId) {
        ThreadUtils.assertOnUiThread();
        BookmarkBridgeJni.get().deleteBookmark(
                mNativeBookmarkBridge, BookmarkBridge.this, bookmarkId);
    }

    /**
     * Removes all the non-permanent bookmark nodes that are editable by the user. Observers are
     * only notified when all nodes have been removed. There is no notification for individual node
     * removals.
     */
    public void removeAllUserBookmarks() {
        ThreadUtils.assertOnUiThread();
        BookmarkBridgeJni.get().removeAllUserBookmarks(mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /**
     * Move the bookmark to the new index within same folder or to a different folder.
     * @param bookmarkId The id of the bookmark that is being moved.
     * @param newParentId The parent folder id.
     * @param index The new index for the bookmark.
     */
    public void moveBookmark(BookmarkId bookmarkId, BookmarkId newParentId, int index) {
        ThreadUtils.assertOnUiThread();
        BookmarkBridgeJni.get().moveBookmark(
                mNativeBookmarkBridge, BookmarkBridge.this, bookmarkId, newParentId, index);
    }

    /**
     * Add a new folder to the given parent folder
     *
     * @param parent Folder where to add. Must be a normal editable folder, instead of a partner
     *               bookmark folder or a managed bookmark folder or root node of the entire
     *               bookmark model.
     * @param index The position to locate the new folder
     * @param title The title text of the new folder
     * @return Id of the added node. If adding failed (index is invalid, string is null, parent is
     *         not editable), returns null.
     */
    public BookmarkId addFolder(BookmarkId parent, int index, String title) {
        ThreadUtils.assertOnUiThread();
        assert parent.getType() == BookmarkType.NORMAL;
        assert index >= 0;
        assert title != null;

        return BookmarkBridgeJni.get().addFolder(
                mNativeBookmarkBridge, BookmarkBridge.this, parent, index, title);
    }

    /**
     * Add a new bookmark to a specific position below parent.
     *
     * @param parent Folder where to add. Must be a normal editable folder, instead of a partner
     *               bookmark folder or a managed bookmark folder or root node of the entire
     *               bookmark model.
     * @param index The position where the bookmark will be placed in parent folder
     * @param title Title of the new bookmark. If empty, the URL will be used as the title.
     * @param url Url of the new bookmark
     * @return Id of the added node. If adding failed (index is invalid, string is null, parent is
     *         not editable), returns null.
     */
    public BookmarkId addBookmark(BookmarkId parent, int index, String title, GURL url) {
        ThreadUtils.assertOnUiThread();
        assert parent.getType() == BookmarkType.NORMAL;
        assert index >= 0;
        assert title != null;
        assert url != null;

        recordBookmarkAdded();

        if (TextUtils.isEmpty(title)) title = url.getSpec();
        return BookmarkBridgeJni.get().addBookmark(
                mNativeBookmarkBridge, this, parent, index, title, url);
    }

    /** Record the user action for adding a bookmark. */
    private void recordBookmarkAdded() {
        RecordUserAction.record("BookmarkAdded");
    }

    /**
     * Undo the last undoable action on the top of the bookmark undo stack
     */
    public void undo() {
        ThreadUtils.assertOnUiThread();
        BookmarkBridgeJni.get().undo(mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /**
     * Start grouping actions for a single undo operation
     * Note: This only works with BookmarkModel, not partner bookmarks.
     */
    public void startGroupingUndos() {
        ThreadUtils.assertOnUiThread();
        BookmarkBridgeJni.get().startGroupingUndos(mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /**
     * End grouping actions for a single undo operation
     * Note: This only works with BookmarkModel, not partner bookmarks.
     */
    public void endGroupingUndos() {
        ThreadUtils.assertOnUiThread();
        BookmarkBridgeJni.get().endGroupingUndos(mNativeBookmarkBridge, BookmarkBridge.this);
    }

    public boolean isEditBookmarksEnabled() {
        ThreadUtils.assertOnUiThread();
        if (mNativeBookmarkBridge == 0) return false;
        return BookmarkBridgeJni.get().isEditBookmarksEnabled(mNativeBookmarkBridge);
    }

    /** Gets the profile. */
    protected Profile getProfile() {
        return mProfile;
    }

    /**
     * Notifies the observer that bookmark model has been loaded.
     */
    @VisibleForTesting
    public void notifyBookmarkModelLoaded() {
        // Call isBookmarkModelLoaded() to do the check since it could be overridden by the child
        // class to add the addition logic.
        if (isBookmarkModelLoaded()) {
            for (BookmarkModelObserver observer : mObservers) {
                observer.bookmarkModelLoaded();
            }
        }
    }

    /**
     * Reorders the bookmarks of the folder "parent" to be as specified by newOrder.
     *
     * @param parent The parent folder for the reordered bookmarks.
     * @param newOrder A list of bookmark IDs that represents the new order for these bookmarks.
     */
    public void reorderBookmarks(BookmarkId parent, long[] newOrder) {
        ThreadUtils.assertOnUiThread();
        BookmarkBridgeJni.get().reorderChildren(
                mNativeBookmarkBridge, BookmarkBridge.this, parent, newOrder);
    }

    /**
     * Adds an article to the reading list. If the article was already bookmarked, the existing
     * bookmark ID will be returned.
     * @param title The title to be used for the reading list item.
     * @param url The URL of the reading list item.
     * @return The bookmark ID created after saving the article to the reading list, or null on
     *         error.
     */
    public @Nullable BookmarkId addToReadingList(String title, GURL url) {
        ThreadUtils.assertOnUiThread();
        assert title != null;
        assert url != null;
        assert mIsNativeBookmarkModelLoaded;

        return BookmarkBridgeJni.get().addToReadingList(
                mNativeBookmarkBridge, BookmarkBridge.this, title, url);
    }

    /**
     * @param url The URL of the reading list item.
     * @return The reading list item with the URL, or null if no such reading list item.
     */
    public BookmarkItem getReadingListItem(GURL url) {
        ThreadUtils.assertOnUiThread();
        assert url != null;
        assert mIsNativeBookmarkModelLoaded;

        return BookmarkBridgeJni.get().getReadingListItem(
                mNativeBookmarkBridge, BookmarkBridge.this, url);
    }

    /**
     * Helper method to mark an article as read.
     * @param url The URL of the reading list item.
     * @param read Whether the article should be marked as read.
     */
    public void setReadStatusForReadingList(GURL url, boolean read) {
        BookmarkBridgeJni.get().setReadStatus(
                mNativeBookmarkBridge, BookmarkBridge.this, url, read);
    }

    /**
     * Checks whether supplied URL has already been bookmarked.
     * @param url The URL to check.
     * @return Whether the URL has been bookmarked.
     */
    public boolean isBookmarked(GURL url) {
        return BookmarkBridgeJni.get().isBookmarked(mNativeBookmarkBridge, url);
    }

    public BookmarkId getPartnerFolderId() {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getPartnerFolderId(
                mNativeBookmarkBridge, BookmarkBridge.this);
    }

    @CalledByNative
    private void bookmarkModelLoaded() {
        mIsNativeBookmarkModelLoaded = true;

        notifyBookmarkModelLoaded();

        if (!mDelayedBookmarkCallbacks.isEmpty()) {
            for (int i = 0; i < mDelayedBookmarkCallbacks.size(); i++) {
                mDelayedBookmarkCallbacks.get(i).callCallbackMethod();
            }
            mDelayedBookmarkCallbacks.clear();
        }
    }

    @CalledByNative
    private void destroyFromNative() {
        destroy();
    }

    @CalledByNative
    private void bookmarkNodeMoved(
            BookmarkItem oldParent, int oldIndex, BookmarkItem newParent, int newIndex) {
        if (mIsDoingExtensiveChanges) return;

        try { // Vivaldi: Catch potential exceptions here to avoid native crash. Ref. VAB-5621.
        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkNodeMoved(oldParent, oldIndex, newParent, newIndex);
        }
        } catch (Exception ignored) {}
    }

    @CalledByNative
    private void bookmarkNodeAdded(BookmarkItem parent, int index) {
        if (mIsDoingExtensiveChanges) return;

        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkNodeAdded(parent, index);
        }
    }

    @CalledByNative
    private void bookmarkNodeRemoved(BookmarkItem parent, int oldIndex, BookmarkItem node) {
        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkNodeRemoved(parent, oldIndex, node,
                    mIsDoingExtensiveChanges);
        }
    }

    @CalledByNative
    private void bookmarkAllUserNodesRemoved() {
        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkAllUserNodesRemoved();
        }
    }

    @CalledByNative
    private void bookmarkNodeChanged(BookmarkItem node) {
        if (mIsDoingExtensiveChanges) return;

        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkNodeChanged(node);
        }
    }

    @CalledByNative
    private void bookmarkNodeChildrenReordered(BookmarkItem node) {
        if (mIsDoingExtensiveChanges) return;

        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkNodeChildrenReordered(node);
        }
    }

    @CalledByNative
    private void extensiveBookmarkChangesBeginning() {
        mIsDoingExtensiveChanges = true;
    }

    @CalledByNative
    private void extensiveBookmarkChangesEnded() {
        mIsDoingExtensiveChanges = false;
        bookmarkModelChanged();
    }

    @CalledByNative
    private void bookmarkModelChanged() {
        if (mIsDoingExtensiveChanges) return;

        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkModelChanged();
        }
    }

    @CalledByNative
    private void editBookmarksEnabledChanged() {
        for (BookmarkModelObserver observer : mObservers) {
            observer.editBookmarksEnabledChanged();
        }
    }

    /**
     * Get updated price information for a given list of bookmark IDs.
     * @param bookmarksToUpdate The IDs of the bookmarks to update.
     * @param callback A callback to be called for each ID iff price information is available.
     */
    public void getUpdatedProductPrices(
            List<BookmarkId> bookmarksToUpdate, PriceUpdateCallback callback) {
        final HashMap<GURL, BookmarkId> urlToId = new HashMap<>();
        GURL[] urls = new GURL[bookmarksToUpdate.size()];
        for (int i = 0; i < bookmarksToUpdate.size(); i++) {
            GURL url = getBookmarkById(bookmarksToUpdate.get(i)).getUrl();
            urls[i] = url;
            urlToId.put(url, bookmarksToUpdate.get(i));
        }

        // This wrapper is used to avoid needing to pass the map of URL -> BookmarkId to native.
        PriceUpdateCallback callbackWrapper = new PriceUpdateCallback() {
            @Override
            public void onPriceUpdated(BookmarkId id, GURL url, ProductPrice price) {
                callback.onPriceUpdated(urlToId.get(url), url, price);
            }
        };

        BookmarkBridgeJni.get().getUpdatedProductPrices(
                mNativeBookmarkBridge, BookmarkBridge.this, urls, callbackWrapper);
    }

    @CalledByNative
    private void onProductPriceUpdated(
            GURL url, byte[] productPriceBytes, PriceUpdateCallback callback) {
        try {
            ProductPrice price = ProductPrice.parseFrom(productPriceBytes);
            // Intentionally pass |null| for the bookmark ID here, the wrapper will populate this
            // field. See #getUpdatedProductPrices
            callback.onPriceUpdated(null, url, price);
        } catch (InvalidProtocolBufferException ex) {
            // Intentional noop
        }
    }

    @CalledByNative
    private static BookmarkItem createBookmarkItem(long id, int type, String title, GURL url,
            boolean isFolder, long parentId, int parentIdType, boolean isEditable,
            boolean isManaged, long dateAdded, boolean read) {
        return new BookmarkItem(new BookmarkId(id, type), title, url, isFolder,
                new BookmarkId(parentId, parentIdType), isEditable, isManaged, dateAdded, read);
    }

    @CalledByNative
    private static void addToList(List<BookmarkItem> bookmarksList, BookmarkItem bookmark) {
        bookmarksList.add(bookmark);
    }

    @CalledByNative
    private static void addToBookmarkIdList(List<BookmarkId> bookmarkIdList, long id, int type) {
        bookmarkIdList.add(new BookmarkId(id, type));
    }

    @CalledByNative
    private static void addToBookmarkIdListWithDepth(List<BookmarkId> folderList, long id,
            int type, List<Integer> depthList, int depth) {
        folderList.add(new BookmarkId(id, type));
        depthList.add(depth);
    }

    private static List<Pair<Integer, Integer>> createPairsList(int[] left, int[] right) {
        List<Pair<Integer, Integer>> pairList = new ArrayList<Pair<Integer, Integer>>();
        for (int i = 0; i < left.length; i++) {
            pairList.add(new Pair<Integer, Integer>(left[i], right[i]));
        }
        return pairList;
    }

    /**
     * Details about callbacks that need to be called once the bookmark model has loaded.
     */
    private static class DelayedBookmarkCallback {

        private static final int GET_BOOKMARKS_FOR_FOLDER = 0;
        private static final int GET_CURRENT_FOLDER_HIERARCHY = 1;

        private final BookmarksCallback mCallback;
        private final BookmarkId mFolderId;
        private final int mCallbackMethod;
        private final BookmarkBridge mHandler;

        private DelayedBookmarkCallback(BookmarkId folderId, BookmarksCallback callback,
                int method, BookmarkBridge handler) {
            mFolderId = folderId;
            mCallback = callback;
            mCallbackMethod = method;
            mHandler = handler;
        }

        /**
         * Invoke the callback method.
         */
        private void callCallbackMethod() {
            switch (mCallbackMethod) {
                case GET_BOOKMARKS_FOR_FOLDER:
                    mHandler.getBookmarksForFolder(mFolderId, mCallback);
                    break;
                case GET_CURRENT_FOLDER_HIERARCHY:
                    mHandler.getCurrentFolderHierarchy(mFolderId, mCallback);
                    break;
                default:
                    assert false;
                    break;
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @NativeMethods
    public interface Natives {
        BookmarkId getBookmarkIdForWebContents(long nativeBookmarkBridge, BookmarkBridge caller,
                WebContents webContents, boolean onlyEditable);
        BookmarkItem getBookmarkByID(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type);
        void getTopLevelFolderParentIDs(
                long nativeBookmarkBridge, BookmarkBridge caller, List<BookmarkId> bookmarksList);
        void getTopLevelFolderIDs(long nativeBookmarkBridge, BookmarkBridge caller,
                boolean getSpecial, boolean getNormal, List<BookmarkId> bookmarksList);
        BookmarkId getReadingListFolder(long nativeBookmarkBridge, BookmarkBridge caller);
        void getAllFoldersWithDepths(long nativeBookmarkBridge, BookmarkBridge caller,
                List<BookmarkId> folderList, List<Integer> depthList);
        BookmarkId getRootFolderId(long nativeBookmarkBridge, BookmarkBridge caller);
        BookmarkId getMobileFolderId(long nativeBookmarkBridge, BookmarkBridge caller);
        BookmarkId getOtherFolderId(long nativeBookmarkBridge, BookmarkBridge caller);
        BookmarkId getDesktopFolderId(long nativeBookmarkBridge, BookmarkBridge caller);
        BookmarkId getPartnerFolderId(long nativeBookmarkBridge, BookmarkBridge caller);
        String getBookmarkGuidByIdForTesting(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type);
        int getChildCount(long nativeBookmarkBridge, BookmarkBridge caller, long id, int type);
        void getChildIDs(long nativeBookmarkBridge, BookmarkBridge caller, long id, int type,
                List<BookmarkId> bookmarksList);
        BookmarkId getChildAt(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type, int index);
        int getTotalBookmarkCount(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type);
        void getUpdatedProductPrices(long nativeBookmarkBridge, BookmarkBridge caller, GURL[] gurls,
                PriceUpdateCallback callback);
        void setBookmarkTitle(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type, String title);
        void setBookmarkUrl(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type, GURL url);
        byte[] getPowerBookmarkMeta(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type);
        void setPowerBookmarkMeta(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type, byte[] meta);
        void deletePowerBookmarkMeta(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type);
        boolean doesBookmarkExist(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type);
        void getBookmarksForFolder(long nativeBookmarkBridge, BookmarkBridge caller,
                BookmarkId folderId, BookmarksCallback callback, List<BookmarkItem> bookmarksList);
        boolean isFolderVisible(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type);
        void getCurrentFolderHierarchy(long nativeBookmarkBridge, BookmarkBridge caller,
                BookmarkId folderId, BookmarksCallback callback, List<BookmarkItem> bookmarksList);
        BookmarkId addFolder(long nativeBookmarkBridge, BookmarkBridge caller, BookmarkId parent,
                int index, String title);
        void deleteBookmark(
                long nativeBookmarkBridge, BookmarkBridge caller, BookmarkId bookmarkId);
        void removeAllUserBookmarks(long nativeBookmarkBridge, BookmarkBridge caller);
        void moveBookmark(long nativeBookmarkBridge, BookmarkBridge caller, BookmarkId bookmarkId,
                BookmarkId newParentId, int index);
        BookmarkId addBookmark(long nativeBookmarkBridge, BookmarkBridge caller, BookmarkId parent,
                int index, String title, GURL url);
        BookmarkId addToReadingList(
                long nativeBookmarkBridge, BookmarkBridge caller, String title, GURL url);
        BookmarkItem getReadingListItem(long nativeBookmarkBridge, BookmarkBridge caller, GURL url);
        void setReadStatus(
                long nativeBookmarkBridge, BookmarkBridge caller, GURL url, boolean read);
        void undo(long nativeBookmarkBridge, BookmarkBridge caller);
        void startGroupingUndos(long nativeBookmarkBridge, BookmarkBridge caller);
        void endGroupingUndos(long nativeBookmarkBridge, BookmarkBridge caller);
        void loadEmptyPartnerBookmarkShimForTesting(
                long nativeBookmarkBridge, BookmarkBridge caller);
        void loadFakePartnerBookmarkShimForTesting(
                long nativeBookmarkBridge, BookmarkBridge caller);
        void searchBookmarks(long nativeBookmarkBridge, BookmarkBridge caller,
                List<BookmarkId> bookmarkMatches, String query, String[] tags,
                int powerBookmarkType, int maxNumber);
        void getBookmarksOfType(long nativeBookmarkBridge, BookmarkBridge caller,
                List<BookmarkId> bookmarkMatches, int powerBookmarkType);
        long init(BookmarkBridge caller, Profile profile);
        boolean isDoingExtensiveChanges(long nativeBookmarkBridge, BookmarkBridge caller);
        void destroy(long nativeBookmarkBridge, BookmarkBridge caller);
        boolean isEditBookmarksEnabled(long nativeBookmarkBridge);
        void reorderChildren(long nativeBookmarkBridge, BookmarkBridge caller, BookmarkId parent,
                long[] orderedNodes);
        boolean isBookmarked(long nativeBookmarkBridge, GURL url);

        /** Vivaldi */
        BookmarkId getTrashFolderId(long nativeBookmarkBridge, BookmarkBridge caller);
        void setBookmarkDescription(long nativeBookmarkBridge, BookmarkBridge caller,
                long id, int type, String description);
        void setBookmarkNickName(
                long nativeBookmarkBridge, BookmarkBridge caller, long id, int type, String nickName);
        void setBookmarkSpeedDial(long nativeBookmarkBridge, BookmarkBridge caller,
                long id, int type, boolean isSpeedDial);
        void setBookmarkThumbnail(long nativeBookmarkBridge, BookmarkBridge caller,
                long id, int type, String path);
        void getSpeedDialFolders(
                long nativeBookmarkBridge, BookmarkBridge caller, List<BookmarkId> folderList);
        void getSpeedDialFoldersWithSubfolders(long nativeBookmarkBridge, BookmarkBridge caller,
                List<BookmarkId> folderList);
        void getChildIDsVivaldi(long nativeBookmarkBridge, BookmarkBridge caller, long id, int type,
                         boolean getFolders, boolean getBookmarks, boolean getSeparators,
                         List<BookmarkId> bookmarksList);
        int getChildCountWithoutSeparators(long nativeBookmarkBridge, BookmarkBridge caller,
                long id, int type);
    }

    /** Vivaldi */
    @CalledByNative
    private static BookmarkItem createVivaldiBookmarkItem(long id, int type,
            String title, GURL url, boolean isFolder, long parentId,
            int parentIdType, boolean isEditable, boolean isManaged,
            long dateAdded, boolean read,
            boolean isSpeeddial, String nickName, String description,
            long created, String thumbnailPath, String guid) {
        return new BookmarkItem(new BookmarkId(id, type), title, url, isFolder,
                new BookmarkId(parentId, parentIdType), isEditable, isManaged,
                dateAdded, read,
                isSpeeddial, nickName, description, created, thumbnailPath, guid);
    }

    /** Vivaldi */
    @CalledByNative
    private void bookmarkSpeedDialNodeChanged(BookmarkItem node) {
        if (mIsDoingExtensiveChanges) return;

        for (BookmarkModelObserver observer : mObservers) {
            observer.bookmarkSpeedDialNodeChanged(node);
        }
    }

    /** Vivaldi
     * @return BookmarkId The BookmarkId for Trash folder node.
     */
    public BookmarkId getTrashFolderId() {
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getTrashFolderId(mNativeBookmarkBridge, BookmarkBridge.this);
    }

    /** Vivaldi
     * Set description of the given bookmark.
     */
    public void setBookmarkDescription(BookmarkId id, String description) {
        assert mIsNativeBookmarkModelLoaded;
        BookmarkBridgeJni.get().setBookmarkDescription(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType(), description);
    }

    /** Vivaldi
     * Set nickname for the given bookmark.
     */
    public void setBookmarkNickName(BookmarkId id, String nickName) {
        assert mIsNativeBookmarkModelLoaded;
        assert id.getType() == BookmarkType.NORMAL;
        BookmarkBridgeJni.get().setBookmarkNickName(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType(), nickName);
    }

    /** Vivaldi
     * Set as speed dial for the given (folder) bookmark.
     */
    public void setBookmarkSpeedDial(BookmarkId id, Boolean isSpeedDial) {
        assert mIsNativeBookmarkModelLoaded;
        BookmarkBridgeJni.get().setBookmarkSpeedDial(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType(), isSpeedDial);
    }

    /** Vivaldi
     * Set as speed dial for the given (folder) bookmark.
     */
    public void setBookmarkThumbnail(BookmarkId id, String thumbnailPath) {
        assert mIsNativeBookmarkModelLoaded;
        BookmarkBridgeJni.get().setBookmarkThumbnail(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType(), thumbnailPath);
    }

    /** Vivaldi
     * Get speed dial folders.
     * @param folderList
     */
    public void getSpeedDialFolders(List<BookmarkId> folderList) {
        assert mIsNativeBookmarkModelLoaded;
        BookmarkBridgeJni.get().getSpeedDialFolders(
                mNativeBookmarkBridge, BookmarkBridge.this, folderList);
    }

    /** Vivaldi
     * Get speed dial folders including folders in subfolder
     * tree of speed dial folders
     * @param folderList
     */
    public void getSpeedDialFoldersWithSubfolders(List<BookmarkId> folderList) {
        assert mIsNativeBookmarkModelLoaded;
        BookmarkBridgeJni.get().getSpeedDialFoldersWithSubfolders(
                mNativeBookmarkBridge, BookmarkBridge.this, folderList);
    }

    /**
     * @return The number of children that the given node has.
     */
    public int getChildCountWithoutSeparators(BookmarkId id) {
        ThreadUtils.assertOnUiThread();
        assert mIsNativeBookmarkModelLoaded;
        return BookmarkBridgeJni.get().getChildCountWithoutSeparators(
                mNativeBookmarkBridge, BookmarkBridge.this, id.getId(), id.getType());
    }
}
