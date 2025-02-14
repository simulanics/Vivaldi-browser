// Copyright 2020 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_CHROME_BROWSER_UI_NTP_NEW_TAB_PAGE_FEATURE_H_
#define IOS_CHROME_BROWSER_UI_NTP_NEW_TAB_PAGE_FEATURE_H_

#include "base/feature_list.h"
#include "components/prefs/pref_service.h"

// Feature flag to enable showing a live preview for Discover feed when opening
// the feed context menu.
BASE_DECLARE_FEATURE(kEnableDiscoverFeedPreview);

// Feature flag to show ghost cards when refreshing the discover feed.
BASE_DECLARE_FEATURE(kDiscoverFeedGhostCardsEnabled);

// Feature flag to enable static resource serving for the Discover feed.
BASE_DECLARE_FEATURE(kEnableDiscoverFeedStaticResourceServing);

// Feature flag to enable discofeed endpoint for the Discover feed.
BASE_DECLARE_FEATURE(kEnableDiscoverFeedDiscoFeedEndpoint);

// Feature flag to enable static resource serving for the Discover feed.
BASE_DECLARE_FEATURE(kEnableDiscoverFeedStaticResourceServing);

// Feature flag to enable the sync promo on top of the discover feed.
BASE_DECLARE_FEATURE(kEnableDiscoverFeedTopSyncPromo);

// Feature flag to enable a default Following feed sort type.
BASE_DECLARE_FEATURE(kFollowingFeedDefaultSortType);

// Feature flag to enable checking feed visibility on attention log start.
BASE_DECLARE_FEATURE(kEnableCheckVisibilityOnAttentionLogStart);

// Feature flag to enable refining data source reload reporting when having a
// very short attention log.
BASE_DECLARE_FEATURE(kEnableRefineDataSourceReloadReporting);

// A parameter to indicate whether Reconstructed Templates is enabled for static
// resource serving.
extern const char kDiscoverFeedSRSReconstructedTemplatesEnabled[];

// A parameter to indicate whether Preload Templates is enabled for static
// resource serving.
extern const char kDiscoverFeedSRSPreloadTemplatesEnabled[];

// A parameter value used for displaying the full with title promo style.
extern const char kDiscoverFeedTopSyncPromoStyleFullWithTitle[];

// A parameter value used for displaying the compact promo style.
extern const char kDiscoverFeedTopSyncPromoStyleCompact[];

// A parameter value for the default Following sort type to be Sort by Latest.
extern const char kFollowingFeedDefaultSortTypeSortByLatest[];

// A parameter value for the default Following sort type to be Grouped by
// Publisher.
extern const char kFollowingFeedDefaultSortTypeGroupedByPublisher[];

// Feature flag to fix the NTP view hierarchy if it is broken before applying
// constraints.
// TODO(crbug.com/1262536): Remove this when it is fixed.
BASE_DECLARE_FEATURE(kNTPViewHierarchyRepair);

// Feature flag to remove the Feed from the NTP.
BASE_DECLARE_FEATURE(kEnableFeedAblation);

// Whether the Discover feed content preview is shown in the context menu.
bool IsDiscoverFeedPreviewEnabled();

// Whether the NTP view hierarchy repair is enabled.
bool IsNTPViewHierarchyRepairEnabled();

// Whether the Discover feed top sync promotion is enabled.
bool IsDiscoverFeedTopSyncPromoEnabled();

// Whether the feed top sync promotion is compact or not.
bool IsDiscoverFeedTopSyncPromoCompact();

// Returns the number of impressions before autodismissing the feed sync promo.
int FeedSyncPromoAutodismissCount();

// Whether the Following feed default sort type experiment is enabled.
bool IsFollowingFeedDefaultSortTypeEnabled();

// Whether the default Following feed sort type is Grouped by Publisher.
bool IsDefaultFollowingFeedSortTypeGroupedByPublisher();

// Whether the Discover feed ablation experiment is enabled.
bool IsFeedAblationEnabled();

// Whether the ghost cards should be shown when refreshing Discover feed
// content.
bool IsDiscoverFeedGhostCardsEnabled();

// Whether content suggestions are enabled for supervised users.
bool IsContentSuggestionsForSupervisedUserEnabled(PrefService* pref_service);

// YES if enabled checking feed visibility on attention log start.
bool IsCheckVisibilityOnAttentionLogStartEnabled();

// YES if enabled refining data source reload reporting when having a very short
// attention log.
bool IsRefineDataSourceReloadReportingEnabled();

#endif  // IOS_CHROME_BROWSER_UI_NTP_NEW_TAB_PAGE_FEATURE_H_
