// Copyright 2021 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.content.res.Resources;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.primitives.UnsignedLongs;

import org.chromium.base.Callback;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.commerce.ShoppingServiceFactory;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.subscriptions.CommerceSubscription;
import org.chromium.chrome.browser.subscriptions.CommerceSubscription.CommerceSubscriptionType;
import org.chromium.chrome.browser.subscriptions.CommerceSubscription.PriceTrackableOffer;
import org.chromium.chrome.browser.subscriptions.CommerceSubscription.SubscriptionManagementType;
import org.chromium.chrome.browser.subscriptions.CommerceSubscription.TrackingIdType;
import org.chromium.chrome.browser.subscriptions.SubscriptionsManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.ui.messages.snackbar.Snackbar;
import org.chromium.chrome.browser.ui.messages.snackbar.SnackbarManager;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.commerce.core.ShoppingService;
import org.chromium.components.power_bookmarks.PowerBookmarkMeta;
import org.chromium.components.power_bookmarks.PowerBookmarkType;
import org.chromium.components.power_bookmarks.ProductPrice;
import org.chromium.components.power_bookmarks.ShoppingSpecifics;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Utilities for use in power bookmarks. */
// TODO(1351830): We should add a JNI layer for the native version of these utilities in
//                price_tracking_utils and use those instead.
public class PowerBookmarkUtils {
    /**
     * Possible results for the validation of client and server-side subscriptions. These need to
     * stay in sync with CommerceSubscriptionValidationResult in enums.xml.
     */
    @IntDef({ValidationResult.CLEAN, ValidationResult.BOOKMARKS_FIXED,
            ValidationResult.SUBSCRIPTIONS_FIXED,
            ValidationResult.BOOKMARKS_AND_SUBSCRIPTIONS_FIXED})
    @Retention(RetentionPolicy.SOURCE)
    private @interface ValidationResult {
        int CLEAN = 0;
        int BOOKMARKS_FIXED = 1;
        int SUBSCRIPTIONS_FIXED = 2;
        int BOOKMARKS_AND_SUBSCRIPTIONS_FIXED = 3;

        int MAX = BOOKMARKS_AND_SUBSCRIPTIONS_FIXED;
    }

    private static Boolean sPriceTrackingEligibleForTesting;
    private static PowerBookmarkMeta sPowerBookmarkMetaForTesting;

    /**
     * Checks if the given tab is price-trackable.
     * @param tab The tab to check for price-tracking eligibility.
     * @return Whether the given tab is eligible for price-tracking.
     */
    public static boolean isPriceTrackingEligible(@Nullable Tab tab) {
        if (tab == null) return false;
        if (sPriceTrackingEligibleForTesting != null) return sPriceTrackingEligibleForTesting;

        ShoppingService service =
                ShoppingServiceFactory.getForProfile(Profile.getLastUsedRegularProfile());
        if (service == null) return false;

        return service.getAvailableProductInfoForUrl(tab.getUrl()) != null;
    }

    /**
     * Lookup the cluster id for the given tab and retrieve the corresponding bookmark id which
     * tracks the cluster id.
     * @param tab The tab to lookup the {@link BookmarkId} for.
     * @param bookmarkModel The {@link BookmarkModel} used to lookup bookmark data.
     * @return The {@link BookmarkId} for the given tab or null.
     */
    public static List<BookmarkId> getBookmarkIdsWithSharedClusterIdForTab(
            @Nullable Tab tab, BookmarkModel bookmarkModel) {
        ShoppingService service =
                ShoppingServiceFactory.getForProfile(Profile.getLastUsedRegularProfile());

        if (tab == null || service == null) return new ArrayList<>();

        ShoppingService.ProductInfo info = service.getAvailableProductInfoForUrl(tab.getUrl());

        if (info == null) return new ArrayList<>();

        return getBookmarkIdsForClusterId(info.productClusterId, bookmarkModel);
    }

    /**
     * Checks if the bookmark associated with the given cluster id has price tracking enabled.
     * @param clusterId The cluster id to lookup.
     * @param bookmarkModel The {@link BookmarkModel} used to lookup bookmark data.
     * @return The {@link BookmarkId} for the given tab or null.
     */
    public static boolean isPriceTrackingEnabledForClusterId(
            Long clusterId, BookmarkModel bookmarkModel) {
        List<BookmarkId> productIds = getBookmarkIdsForClusterId(clusterId, bookmarkModel);
        for (BookmarkId productId : productIds) {
            PowerBookmarkMeta meta = bookmarkModel.getPowerBookmarkMeta(productId);

            // Return any of the bookmarks with the given cluster id are price-tracked.
            if (meta != null && meta.hasShoppingSpecifics()
                    && meta.getShoppingSpecifics().getIsPriceTracked()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Unified way to get the associated {@link CommerceSubscription} for a {@link
     * PowerBookmarkMeta}.
     * @param meta The {@link PowerBookmarkMeta} to create the {@link CommerceSubscription} for.
     * @return The {@link CommerceSubsription} for the given {@link PowerBookmarkMeta}
     */
    public static @NonNull CommerceSubscription createCommerceSubscriptionForPowerBookmarkMeta(
            @NonNull PowerBookmarkMeta meta) {
        ShoppingSpecifics shoppingSpecifics = meta.getShoppingSpecifics();
        // Use UnsignedLongs to convert ProductClusterId to avoid overflow.
        PriceTrackableOffer seenOffer =
                new PriceTrackableOffer(UnsignedLongs.toString(shoppingSpecifics.getOfferId()),
                        Long.toString(shoppingSpecifics.getCurrentPrice().getAmountMicros()),
                        shoppingSpecifics.getCountryCode());
        return new CommerceSubscription(CommerceSubscriptionType.PRICE_TRACK,
                UnsignedLongs.toString(shoppingSpecifics.getProductClusterId()),
                SubscriptionManagementType.USER_MANAGED, TrackingIdType.PRODUCT_CLUSTER_ID,
                seenOffer);
    }

    /**
     * Checks if the given {@link BookmarkId} is price-tracked.
     *
     * @param subscriptionsManager Manages price-tracking subscriptions.
     * @param snackbarManager Manages snackbars, non-null if a message should be sent to alert the
     *         users of price-tracking events.
     * @param bookmarkModel The BookmarkModel used to query bookmarks.
     * @param bookmarkId The BookmarkId to check the price-tracking status of.
     * @param enabled Whether price-tracking should be enabled.
     * @param callback The status callback.
     */
    public static void setPriceTrackingEnabled(@Nullable SubscriptionsManager subscriptionsManager,
            @NonNull BookmarkModel bookmarkModel, @Nullable BookmarkId bookmarkId, boolean enabled,
            Callback<Integer> callback) {
        if (bookmarkId == null || subscriptionsManager == null) return;

        bookmarkModel.finishLoadingBookmarkModel(() -> {
            PowerBookmarkMeta meta = bookmarkModel.getPowerBookmarkMeta(bookmarkId);
            if (meta == null || !meta.hasShoppingSpecifics()) return;

            CommerceSubscription subscription =
                    createCommerceSubscriptionForPowerBookmarkMeta(meta);
            Callback<Integer> wrapperCallback = (status) -> {
                if (bookmarkModel.isDestroyed()) return;
                if (status == SubscriptionsManager.StatusCode.OK) {
                    setPriceTrackingEnabledInMetadata(bookmarkModel, bookmarkId, enabled);
                }
                callback.onResult(status);
            };

            if (enabled) {
                subscriptionsManager.subscribe(subscription, wrapperCallback);
            } else {
                subscriptionsManager.unsubscribe(subscription, wrapperCallback);
            }
        });
    }

    /**
     * Sets the price-tracking status of the given bookmarks.
     *
     * @param subscriptionsManager Manages price-tracking subscriptions.
     * @param bookmarkModel The BookmarkModel used to query bookmarks.
     * @param bookmarkIds A list of BookmarkIds to set the price-tracking status of.
     * @param enabled Whether price-tracking should be enabled.
     * @param snackbarManager Manages snackbars, non-null if a message should be sent to alert the
     *         users of price-tracking events.
     * @param resources Used to retrieve resources.
     */
    public static void setPriceTrackingEnabledWithSnackbars(
            @Nullable SubscriptionsManager subscriptionsManager,
            @NonNull BookmarkModel bookmarkModel, @Nullable List<BookmarkId> bookmarkIds,
            boolean enabled, SnackbarManager snackbarManager, Resources resources) {
        if (bookmarkIds == null || bookmarkIds.size() == 0 || subscriptionsManager == null) return;

        // Only the the first bookmark out of the list needs to query subscriptions manager.
        BookmarkId id = bookmarkIds.get(0);
        setPriceTrackingEnabledWithSnackbars(subscriptionsManager, bookmarkModel, id, enabled,
                snackbarManager, resources, (status) -> {
                    if (status == SubscriptionsManager.StatusCode.OK) {
                        // If the request was successful, set the metadata properly.
                        for (int i = 1; i < bookmarkIds.size(); i++) {
                            setPriceTrackingEnabledInMetadata(
                                    bookmarkModel, bookmarkIds.get(i), enabled);
                        }
                    }
                });
    }

    /**
     * Checks if the given {@link BookmarkId} is price-tracked.
     *
     * @param subscriptionsManager Manages price-tracking subscriptions.
     * @param bookmarkModel The BookmarkModel used to query bookmarks.
     * @param bookmarkId The BookmarkId to check the price-tracking status of.
     * @param enabled Whether price-tracking should be enabled.
     * @param snackbarManager Manages snackbars, non-null if a message should be sent to alert the
     *         users of price-tracking events.
     * @param resources Used to retrieve resources.
     * @param callback The status callback, may be called multiple times depending if the user
     *         retries on failure.
     */
    public static void setPriceTrackingEnabledWithSnackbars(
            @Nullable SubscriptionsManager subscriptionsManager,
            @NonNull BookmarkModel bookmarkModel, @Nullable BookmarkId bookmarkId, boolean enabled,
            SnackbarManager snackbarManager, Resources resources, Callback<Integer> callback) {
        // Action to retry the subscription request on failure.
        SnackbarManager.SnackbarController retrySnackbarControllerAction =
                new SnackbarManager.SnackbarController() {
                    @Override
                    public void onAction(Object actionData) {
                        setPriceTrackingEnabledWithSnackbars(subscriptionsManager, bookmarkModel,
                                bookmarkId, enabled, snackbarManager, resources, callback);
                    }
                };
        // Wrapper which shows a snackbar and forwards the result.
        Callback<Integer> wrapperCallback = (status) -> {
            Snackbar snackbar;
            if (status == SubscriptionsManager.StatusCode.OK) {
                snackbar = Snackbar.make(
                        resources.getString(enabled ? R.string.price_tracking_enabled_snackbar
                                                    : R.string.price_tracking_disabled_snackbar),
                        null, Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_PRICE_TRACKING_SUCCESS);
            } else {
                snackbar =
                        Snackbar.make(resources.getString(R.string.price_tracking_error_snackbar),
                                        retrySnackbarControllerAction, Snackbar.TYPE_NOTIFICATION,
                                        Snackbar.UMA_PRICE_TRACKING_FAILURE)
                                .setAction(resources.getString(
                                                   R.string.price_tracking_error_snackbar_action),
                                        null);
            }
            snackbar.setSingleLine(false);
            snackbarManager.showSnackbar(snackbar);
            callback.onResult(status);
        };
        setPriceTrackingEnabled(
                subscriptionsManager, bookmarkModel, bookmarkId, enabled, wrapperCallback);
    }

    /**
     * Update to the given price for the given bookmark id.
     *
     * @param bookmarkModel Used to read/write bookmark data.
     * @param bookmarkId The bookmark id to update.
     * @param price The price to update to.
     */
    public static void updatePriceForBookmarkId(@NonNull BookmarkModel bookmarkModel,
            @NonNull BookmarkId bookmarkId,
            @NonNull org.chromium.components.commerce.PriceTracking.ProductPrice price) {
        PowerBookmarkMeta meta = bookmarkModel.getPowerBookmarkMeta(bookmarkId);
        if (meta == null) return;
        ProductPrice newPrice = ProductPrice.newBuilder()
                                        .setCurrencyCode(price.getCurrencyCode())
                                        .setAmountMicros(price.getAmountMicros())
                                        .build();
        bookmarkModel.setPowerBookmarkMeta(bookmarkId,
                PowerBookmarkMeta.newBuilder(meta)
                        .setShoppingSpecifics(
                                ShoppingSpecifics.newBuilder(meta.getShoppingSpecifics())
                                        .setCurrentPrice(newPrice)
                                        .build())
                        .build());
    }

    /**
     * Gets the power bookmark associated with the given tab.
     * @param bookmarkModel The {@link BookmarkModel} to retrieve bookmark info.
     * @param tab The current {@link Tab} to check.
     * @return The {@link PowerBookmarkMeta} associated with the given tab or null.
     */
    public static @Nullable PowerBookmarkMeta getBookmarkBookmarkMetaForTab(
            @Nullable BookmarkModel bookmarkModel, @Nullable Tab tab) {
        if (bookmarkModel == null || tab == null) return null;

        BookmarkId bookmarkId = bookmarkModel.getUserBookmarkIdForTab(tab);
        if (bookmarkId == null) return null;

        return bookmarkModel.getPowerBookmarkMeta(bookmarkId);
    }

    /** @return Whether the price tracking flag is set in the bookmark's meta. */
    public static boolean isBookmarkPriceTracked(BookmarkModel model, BookmarkId id) {
        PowerBookmarkMeta meta = model.getPowerBookmarkMeta(id);
        if (meta == null || !meta.hasShoppingSpecifics()) return false;

        return meta.getShoppingSpecifics().getIsPriceTracked();
    }

    private static List<BookmarkId> getBookmarkIdsForClusterId(
            Long clusterId, BookmarkModel bookmarkModel) {
        List<BookmarkId> results = new ArrayList<>();
        List<BookmarkId> products = bookmarkModel.getBookmarksOfType(PowerBookmarkType.SHOPPING);
        if (products == null || products.size() == 0) return results;

        for (BookmarkId product : products) {
            PowerBookmarkMeta meta = bookmarkModel.getPowerBookmarkMeta(product);
            if (meta == null || !meta.hasShoppingSpecifics()) continue;

            Long productClusterId = meta.getShoppingSpecifics().getProductClusterId();
            if (productClusterId.equals(clusterId)) {
                results.add(product);
            }
        }

        return results;
    }

    private static void setPriceTrackingEnabledInMetadata(@NonNull BookmarkModel bookmarkModel,
            @Nullable BookmarkId bookmarkId, boolean enabled) {
        PowerBookmarkMeta meta = bookmarkModel.getPowerBookmarkMeta(bookmarkId);
        if (meta == null || !meta.hasShoppingSpecifics()) return;

        bookmarkModel.setPowerBookmarkMeta(bookmarkId,
                PowerBookmarkMeta.newBuilder(meta)
                        .setShoppingSpecifics(
                                ShoppingSpecifics.newBuilder(meta.getShoppingSpecifics())
                                        .setIsPriceTracked(enabled)
                                        .build())
                        .build());
    }

    /** Sets the price-tracking eligibility to the test value given. */
    public static void setPriceTrackingEligibleForTesting(@Nullable Boolean enabled) {
        sPriceTrackingEligibleForTesting = enabled;
    }

    /** Sets the current page meta to the test value given. */
    public static void setPowerBookmarkMetaForTesting(@Nullable PowerBookmarkMeta meta) {
        sPowerBookmarkMetaForTesting = meta;
    }

    /**
     * Perform a sanity check on the bookmarked products that are considered to be price tracked. We
     * need this because we can't guarantee that the backend successfully completed the update --
     * this is currently done asynchronously without a confirmation. This method gets the current
     * list of subscriptions and compares them to the user's local collection. There are two
     * possible scenarios:
     *
     * 1. The service is missing a subscription that the user has locally. In this case we mark the
     *    local bookmark as not price tracked.
     * 2. The service has a USER_MANAGED subscription that is not in the user's local bookmarks. In
     *    this case we remove the subscription from the service.
     *
     * @param bookmarkModel A means of accessing the user's bookmarks.
     * @param subscriptionsManager A handle to the subscriptions backend.
     */
    public static void validateBookmarkedCommerceSubscriptions(
            BookmarkModel bookmarkModel, SubscriptionsManager subscriptionsManager) {
        if (subscriptionsManager == null || bookmarkModel == null) return;

        Runnable updater = () -> {
            // At this point native should be ready since the bookmark model has loaded.
            if (!ChromeFeatureList.isEnabled(
                        ChromeFeatureList.SHOPPING_LIST_ENABLE_DESYNC_RESOLUTION)) {
                return;
            }

            subscriptionsManager.getSubscriptions(
                    CommerceSubscriptionType.PRICE_TRACK, true, (subscriptions) -> {
                        doBookmarkedSubscriptionValidation(
                                bookmarkModel, subscriptionsManager, subscriptions);
                    });
        };

        // Make sure the bookmark model is loaded prior to attempting to operate on it. Otherwise
        // wait and then execute.
        if (bookmarkModel.isBookmarkModelLoaded()) {
            updater.run();
        } else {
            bookmarkModel.addObserver(new BookmarkModelObserver() {
                @Override
                public void bookmarkModelLoaded() {
                    updater.run();
                    bookmarkModel.removeObserver(this);
                }

                @Override
                public void bookmarkModelChanged() {}
            });
        }
    }

    /** @see #validateBookmarkedCommerceSubscriptions(BookmarkModel, SubscriptionsManager) */
    private static void doBookmarkedSubscriptionValidation(BookmarkModel bookmarkModel,
            SubscriptionsManager subscriptionsManager, List<CommerceSubscription> subscriptions) {
        if (bookmarkModel.isDestroyed() || subscriptions == null) return;

        List<BookmarkId> products =
                bookmarkModel.searchBookmarks("", null, PowerBookmarkType.SHOPPING, -1);

        // Even if we get nothing back from bookmarks, run through the process since we might need
        // to unsubscribe from products on the backend.
        if (products == null) products = new ArrayList<>();

        // Keep two sets of IDs since ID:Bookmark is NOT 1:1. |unusedCusterIds| will be used to
        // remove subscriptions for bookmarks not currently on (removed from) the client.
        HashSet<Long> clusterIdSet = new HashSet<>();
        HashMap<Long, CommerceSubscription> unusedClusterIds = new HashMap<>();

        for (CommerceSubscription c : subscriptions) {
            if (CommerceSubscription.SubscriptionManagementType.USER_MANAGED.equals(
                        c.getManagementType())) {
                long clusterId = UnsignedLongs.parseUnsignedLong(c.getTrackingId());
                clusterIdSet.add(clusterId);
                unusedClusterIds.put(clusterId, c);
            }
        }

        int bookmarkFixCount = 0;
        int subscriptionFixCount = 0;

        // Iterate over all the bookmarked products and ensure the ones that are tracked agree
        // with the ones that the subscription manager thinks are tracked.
        for (BookmarkId product : products) {
            PowerBookmarkMeta meta = bookmarkModel.getPowerBookmarkMeta(product);
            if (!meta.hasShoppingSpecifics()) continue;

            ShoppingSpecifics specifics = meta.getShoppingSpecifics();

            if (!specifics.getIsPriceTracked()) continue;

            if (clusterIdSet.contains(specifics.getProductClusterId())) {
                // A cluster ID is only considered used if the user is still subscribed to it.
                unusedClusterIds.remove(specifics.getProductClusterId());
                continue;
            }

            // Reset the meta using a copy of the existing one, but set the price tracking flag
            // to false.
            ShoppingSpecifics newSpecifics =
                    ShoppingSpecifics.newBuilder(specifics).setIsPriceTracked(false).build();
            bookmarkModel.setPowerBookmarkMeta(product,
                    PowerBookmarkMeta.newBuilder(meta).setShoppingSpecifics(newSpecifics).build());
            bookmarkFixCount++;
        }

        // Finally, unsubscribe from active subscriptions if the bookmark either didn't exist or the
        // bookmark wasn't flagged as being price tracked.
        if (!unusedClusterIds.isEmpty()) {
            ArrayList<CommerceSubscription> itemsToUnsubscribe =
                    new ArrayList<>(unusedClusterIds.values());
            subscriptionFixCount += itemsToUnsubscribe.size();
            subscriptionsManager.unsubscribe(itemsToUnsubscribe, (id) -> {});
        }

        recordValidationResult(bookmarkFixCount, subscriptionFixCount);
    }

    /**
     * Record the result of the subscriptions validation.
     * @param bookmarkFixCount The number of bookmarks that needed updating.
     * @param subscriptionFixCount The number of subscriptions that needed updating on the backend.
     */
    private static void recordValidationResult(int bookmarkFixCount, int subscriptionFixCount) {
        @ValidationResult
        int result = ValidationResult.CLEAN;

        if (bookmarkFixCount > 0 && subscriptionFixCount > 0) {
            result = ValidationResult.BOOKMARKS_AND_SUBSCRIPTIONS_FIXED;
        } else if (bookmarkFixCount > 0) {
            result = ValidationResult.BOOKMARKS_FIXED;
        } else if (subscriptionFixCount > 0) {
            result = ValidationResult.SUBSCRIPTIONS_FIXED;
        }

        RecordHistogram.recordEnumeratedHistogram(
                "Commerce.PowerBookmarks.SubscriptionValidationResult", result,
                ValidationResult.MAX);
    }
}
