// Copyright 2020 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowInsets;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.chromium.base.Callback;
import org.chromium.base.TraceEvent;
import org.chromium.base.metrics.TimingMetric;
import org.chromium.base.task.PostTask;
import org.chromium.chrome.browser.omnibox.OmniboxFeatures;
import org.chromium.chrome.browser.omnibox.R;
import org.chromium.chrome.browser.ui.theme.BrandedColorScheme;
import org.chromium.chrome.browser.util.KeyNavigationUtil;
import org.chromium.components.browser_ui.styles.ChromeColors;
import org.chromium.content_public.browser.UiThreadTaskTraits;
import org.chromium.ui.base.ViewUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Vivaldi
import org.chromium.base.Callback;
import org.chromium.chrome.browser.omnibox.LocationBarLayout;
import org.chromium.chrome.browser.omnibox.LocationBarPhone;
import org.chromium.chrome.browser.omnibox.LocationBarTablet;
import org.chromium.chrome.browser.preferences.SharedPreferencesManager;

import org.vivaldi.browser.suggestions.SearchEngineSuggestionView.LayoutMargins;


/** A widget for showing a list of omnibox suggestions. */
public class OmniboxSuggestionsDropdown extends RecyclerView {
    private static final long DEFERRED_INITIAL_SHRINKING_LAYOUT_FROM_IME_DURATION_MS = 300;
    /**
     * Used to defer the accessibility announcement for list content.
     * This makes core difference when the list is first shown up, when the interaction with the
     * Omnibox and presence of virtual keyboard may actually cause throttling of the Accessibility
     * events.
     */
    private static final long LIST_COMPOSITION_ACCESSIBILITY_ANNOUNCEMENT_DELAY_MS = 300;

    private final int mStandardBgColor;
    private final int mIncognitoBgColor;

    private final int[] mTempPosition = new int[2];
    private final Rect mTempRect = new Rect();
    private final SuggestionLayoutScrollListener mLayoutScrollListener;

    private @Nullable OmniboxSuggestionsDropdownAdapter mAdapter;
    private @Nullable OmniboxSuggestionsDropdownEmbedder mEmbedder;
    private @Nullable GestureObserver mGestureObserver;
    private @Nullable Callback<Integer> mHeightChangeListener;
    private @Nullable Runnable mSuggestionDropdownScrollListener;
    private @Nullable Runnable mSuggestionDropdownOverscrolledToTopListener;
    private @Nullable View mAnchorView;
    private @Nullable View mAlignmentView;
    private @Nullable OnGlobalLayoutListener mAnchorViewLayoutListener;
    private @Nullable View.OnLayoutChangeListener mAlignmentViewLayoutListener;

    private int mListViewMaxHeight;
    private int mLastBroadcastedListViewMaxHeight;

    @IntDef({InitialResizeState.WAITING_FOR_FIRST_MEASURE, InitialResizeState.WAITING_FOR_SHRINKING,
            InitialResizeState.IGNORING_SHRINKING, InitialResizeState.HANDLED_INITIAL_SIZING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface InitialResizeState {
        int WAITING_FOR_FIRST_MEASURE = 0;
        int WAITING_FOR_SHRINKING = 1;
        int IGNORING_SHRINKING = 2;
        int HANDLED_INITIAL_SIZING = 3;
    }

    @InitialResizeState
    private int mInitialResizeState = InitialResizeState.WAITING_FOR_FIRST_MEASURE;
    private int mWidthMeasureSpec;
    private int mHeightMeasureSpec;

    // Vivaldi
    private LocationBarLayout mLocationBarLayout;
    private static Callback mSearchEngineSuggestionCallback;
    private LayoutMargins layoutMargins = new LayoutMargins(0,0,0,0);

    /**
     * Interface that will receive notifications when the user is interacting with an item on the
     * Suggestions list.
     */
    public interface GestureObserver {
        /**
         * Notify that the user is interacting with an item on the Suggestions list.
         *
         * @param isGestureUp Whether user pressed (false) or depressed (true) the element on the
         *         list.
         * @param timestamp The timestamp associated with the event.
         */
        void onGesture(boolean isGestureUp, long timestamp);
    }

    /** Scroll manager that propagates scroll event notification to registered observers. */
    @VisibleForTesting
    /* package */ class SuggestionLayoutScrollListener extends LinearLayoutManager {
        private boolean mLastKeyboardShowState;

        public SuggestionLayoutScrollListener(Context context) {
            super(context);
            mLastKeyboardShowState = true;
        }

        @Override
        public int scrollVerticallyBy(
                int deltaY, RecyclerView.Recycler recycler, RecyclerView.State state) {
            int scrollY = super.scrollVerticallyBy(deltaY, recycler, state);
            return updateKeyboardVisibilityAndScroll(scrollY, deltaY);
        }

        /**
         * Respond to scroll event.
         * - Upon first scroll down, suppresses the scroll delta and dismisses the
         *   keyboard,
         * - Subsequent scroll down actions should result in scroll,
         * - Upon overscroll to top (= when the list is already on top and a scroll up is
         *   requested), request keyboard to show up.
         *
         * @param scrollY The current vertical scroll position.
         * @param deltaY The requested scroll delta.
         * @return Value of scrollY, if scroll is permitted, or 0 when it is suppressed.
         */
        @VisibleForTesting
        /* package */ int updateKeyboardVisibilityAndScroll(int scrollY, int deltaY) {
            boolean keyboardShouldShow = (scrollY == 0 && deltaY <= 0);

            // Vivaldi: This avoids the blinking of suggestion results as the keyboard goes on and
            // off when address bar is at bottom. Ref - VAB-6613
            if (isAddressBarAtBottom()) keyboardShouldShow = true;

            if (mLastKeyboardShowState == keyboardShouldShow) return scrollY;
            mLastKeyboardShowState = keyboardShouldShow;

            if (keyboardShouldShow) {
                if (mSuggestionDropdownOverscrolledToTopListener != null) {
                    mSuggestionDropdownOverscrolledToTopListener.run();
                }
            } else {
                if (mSuggestionDropdownScrollListener != null) {
                    mSuggestionDropdownScrollListener.run();
                }
                return 0;
            }
            return scrollY;
        }

        /**
         * Reset the internal keyboard state.
         * This needs to be called either when the SuggestionsDropdown is hidden or shown again
         * to reflect either the end of the current or beginning of the next interaction
         * session.
         */
        @VisibleForTesting
        /* package */ void resetKeyboardShowState() {
            mLastKeyboardShowState = true;
        }
    }
    /**
     * RecyclerView pool that records performance of the view recycling mechanism.
     * @see OmniboxSuggestionsListViewListAdapter#canReuseView(View, int)
     */
    private class HistogramRecordingRecycledViewPool extends RecycledViewPool {
        HistogramRecordingRecycledViewPool() {
            // The list below should include suggestions defined in OmniboxSuggestionUiType
            // and specify the maximum anticipated volume of suggestions of each type.
            // For readability reasons, keep the order of this list same as the order of
            // the types defined in OmniboxSuggestionUiType.
            setMaxRecycledViews(OmniboxSuggestionUiType.DEFAULT, 20);
            setMaxRecycledViews(OmniboxSuggestionUiType.EDIT_URL_SUGGESTION, 1);
            setMaxRecycledViews(OmniboxSuggestionUiType.ANSWER_SUGGESTION, 1);
            if (OmniboxFeatures.shouldRemoveExcessiveRecycledViewClearCalls()) {
                setMaxRecycledViews(OmniboxSuggestionUiType.ENTITY_SUGGESTION, 8);
            } else {
                setMaxRecycledViews(OmniboxSuggestionUiType.ENTITY_SUGGESTION, 5);
            }

            setMaxRecycledViews(OmniboxSuggestionUiType.TAIL_SUGGESTION, 15);
            setMaxRecycledViews(OmniboxSuggestionUiType.CLIPBOARD_SUGGESTION, 1);
            setMaxRecycledViews(OmniboxSuggestionUiType.HEADER, 4);
            setMaxRecycledViews(OmniboxSuggestionUiType.TILE_NAVSUGGEST, 1);
            setMaxRecycledViews(OmniboxSuggestionUiType.PEDAL_SUGGESTION, 3);
            setMaxRecycledViews(OmniboxSuggestionUiType.DIVIDER_LINE, 1);
        }

        @Override
        public ViewHolder getRecycledView(int viewType) {
            ViewHolder result = super.getRecycledView(viewType);
            SuggestionsMetrics.recordSuggestionViewReused(result != null);
            if (result == null) {
                SuggestionsMetrics.recordSuggestionsViewCreatedType(viewType);
            } else {
                SuggestionsMetrics.recordSuggestionsViewReusedType(viewType);
            }
            return result;
        }
    }

    /**
     * Constructs a new list designed for containing omnibox suggestions.
     * @param context Context used for contained views.
     */
    public OmniboxSuggestionsDropdown(@NonNull Context context) {
        super(context, null, android.R.attr.dropDownListViewStyle);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setRecycledViewPool(new HistogramRecordingRecycledViewPool());

        // By default RecyclerViews come with item animators.
        setItemAnimator(null);

        mLayoutScrollListener = new SuggestionLayoutScrollListener(context);
        setLayoutManager(mLayoutScrollListener);

        boolean shouldShowModernizeVisualUpdate =
                OmniboxFeatures.shouldShowModernizeVisualUpdate(context);
        final Resources resources = context.getResources();
        int paddingBottom =
                resources.getDimensionPixelOffset(R.dimen.omnibox_suggestion_list_padding_bottom);
        ViewCompat.setPaddingRelative(this, 0, 0, 0, paddingBottom);

        mStandardBgColor = shouldShowModernizeVisualUpdate
                ? ChromeColors.getSurfaceColor(
                        context, R.dimen.omnibox_suggestion_dropdown_bg_elevation)
                : ChromeColors.getDefaultThemeColor(context, false);
        mIncognitoBgColor = shouldShowModernizeVisualUpdate
                ? context.getColor(R.color.omnibox_dropdown_bg_incognito)
                : ChromeColors.getDefaultThemeColor(context, true);
    }

    /** Get the Android View implementing suggestion list. */
    public @NonNull ViewGroup getViewGroup() {
        return this;
    }

    /** Clean up resources and remove observers installed by this class. */
    public void destroy() {
        getRecycledViewPool().clear();
        mGestureObserver = null;
        mHeightChangeListener = null;
        mSuggestionDropdownScrollListener = null;
        mSuggestionDropdownOverscrolledToTopListener = null;

        mAnchorView.getViewTreeObserver().removeOnGlobalLayoutListener(mAnchorViewLayoutListener);
        if (mAlignmentView != null) {
            mAlignmentView.removeOnLayoutChangeListener(mAlignmentViewLayoutListener);
        }
        mAlignmentView = null;
        mAlignmentViewLayoutListener = null;
    }

    /**
     * Sets the observer for that the user is interacting with an item on the Suggestions list..
     * @param observer an observer of this gesture.
     */
    public void setGestureObserver(@NonNull OmniboxSuggestionsDropdown.GestureObserver observer) {
        mGestureObserver = observer;
    }

    /**
     * Sets the listener for changes of the suggestion list's height.
     * The height may change as a result of eg. soft keyboard popping up.
     *
     * @param listener A listener will receive the new height of the suggestion list in pixels.
     */
    public void setHeightChangeListener(@NonNull Callback<Integer> listener) {
        mHeightChangeListener = listener;
    }

    /**
     * @param listener A listener will be invoked whenever the User scrolls the list.
     */
    public void setSuggestionDropdownScrollListener(@NonNull Runnable listener) {
        mSuggestionDropdownScrollListener = listener;
    }

    /**
     * @param listener A listener will be invoked whenever the User scrolls the list to the top.
     */
    public void setSuggestionDropdownOverscrolledToTopListener(@NonNull Runnable listener) {
        mSuggestionDropdownOverscrolledToTopListener = listener;
    }

    /** Resets selection typically in response to changes to the list. */
    public void resetSelection() {
        if (mAdapter == null) return;
        mAdapter.resetSelection();
    }

    /** @return The number of items in the list. */
    public int getDropdownItemViewCountForTest() {
        if (mAdapter == null) return 0;
        return mAdapter.getItemCount();
    }

    /** @return The Suggestion view at specific index. */
    public @Nullable View getDropdownItemViewForTest(int index) {
        final LayoutManager manager = getLayoutManager();
        manager.scrollToPosition(index);
        return manager.findViewByPosition(index);
    }

    // TODO(crbug.com/1373795): Remove this function after feature
    // OmniboxRemoveExcessiveRecycledViewClearCalls is released to stable and ready to be removed.
    /** Show (and properly size) the suggestions list. */
    public void show() {
        if (OmniboxFeatures.shouldRemoveExcessiveRecycledViewClearCalls()) return;

        if (getVisibility() == VISIBLE) return;

        setVisibility(VISIBLE);
        if (mAdapter != null && mAdapter.getSelectedViewIndex() != 0) {
            mAdapter.resetSelection();
        }

        mLayoutScrollListener.resetKeyboardShowState();
    }

    // TODO(crbug.com/1373795): Remove this function after feature
    // OmniboxRemoveExcessiveRecycledViewClearCalls is released to stable and ready to be removed.
    /** Hide the suggestions list and release any cached resources. */
    public void hide() {
        if (OmniboxFeatures.shouldRemoveExcessiveRecycledViewClearCalls()) return;

        if (getVisibility() != VISIBLE) return;
        setVisibility(GONE);
        getRecycledViewPool().clear();
        // Vivaldi
        mSearchEngineSuggestionCallback = null;
    }

    /**
     * Update the suggestion popup background to reflect the current state.
     * @param brandedColorScheme The {@link @BrandedColorScheme}.
     */
    public void refreshPopupBackground(@BrandedColorScheme int brandedColorScheme) {
        int color = brandedColorScheme == BrandedColorScheme.INCOGNITO ? mIncognitoBgColor
                                                                       : mStandardBgColor;
        if (!isHardwareAccelerated()) {
            // When HW acceleration is disabled, changing mSuggestionList' items somehow erases
            // mOmniboxResultsContainer' background from the area not covered by
            // mSuggestionList. To make sure mOmniboxResultsContainer is always redrawn, we make
            // list background color slightly transparent. This makes mSuggestionList.isOpaque()
            // to return false, and forces redraw of the parent view (mOmniboxResultsContainer).
            if (Color.alpha(color) == 255) {
                color = Color.argb(254, Color.red(color), Color.green(color), Color.blue(color));
            }
        }
        setBackground(new ColorDrawable(color));
    }

    @Override
    public void setAdapter(@NonNull Adapter adapter) {
        mAdapter = (OmniboxSuggestionsDropdownAdapter) adapter;
        super.setAdapter(mAdapter);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mInitialResizeState = InitialResizeState.WAITING_FOR_FIRST_MEASURE;
        mAnchorView.getViewTreeObserver().addOnGlobalLayoutListener(mAnchorViewLayoutListener);
        if (mAlignmentView != null) {
            adjustSidePadding();
            mAlignmentView.addOnLayoutChangeListener(mAlignmentViewLayoutListener);
        }

        if (OmniboxFeatures.shouldRemoveExcessiveRecycledViewClearCalls()) {
            resetSelection();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getRecycledViewPool().clear();
        mAnchorView.getViewTreeObserver().removeOnGlobalLayoutListener(mAnchorViewLayoutListener);
        if (mAlignmentView != null) {
            mAlignmentView.removeOnLayoutChangeListener(mAlignmentViewLayoutListener);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try (TraceEvent tracing = TraceEvent.scoped("OmniboxSuggestionsList.Measure");
                TimingMetric metric = SuggestionsMetrics.recordSuggestionListMeasureTime()) {
            int anchorBottomRelativeToContent = calculateAnchorBottomRelativeToContent();
            maybeUpdateLayoutParams(anchorBottomRelativeToContent);

            int availableViewportHeight =
                    calculateAvailableViewportHeight(anchorBottomRelativeToContent);
            int desiredWidth = mAnchorView.getMeasuredWidth();
            // Suppress the initial requests to shrink the viewport of the omnibox suggestion
            // dropdown. The viewport will decrease when the keyboard is triggered, but the request
            // to resize happens when the keyboard starts showing before it has had the chance to
            // animate in. Because the resizing is triggered early, the dropdown shrinks earlier
            // then the keyboard is fully visible, which leaves a hole in the UI showing the content
            // where the keyboard will eventually go.
            //
            // The work around is to suppress these initial shrinking layout requests and defer them
            // for enough time for the keyboard to hopefully be visible.
            //
            // This does not use getMeasuredHeight() as a means of comparison against the available
            // viewport because on tablets the measured height can be smaller than the viewport as
            // tablets use AT_MOST for the measure spec vs EXACTLY on phones.
            if ((mInitialResizeState == InitialResizeState.WAITING_FOR_SHRINKING
                        || mInitialResizeState == InitialResizeState.IGNORING_SHRINKING)
                    && availableViewportHeight < mListViewMaxHeight
                    && getMeasuredWidth() == desiredWidth) {
                super.onMeasure(mWidthMeasureSpec, mHeightMeasureSpec);
                if (mInitialResizeState == InitialResizeState.IGNORING_SHRINKING) return;

                mInitialResizeState = InitialResizeState.IGNORING_SHRINKING;
                PostTask.postDelayedTask(UiThreadTaskTraits.USER_BLOCKING, () -> {
                    if (mInitialResizeState != InitialResizeState.IGNORING_SHRINKING) return;
                    requestLayout();
                    mInitialResizeState = InitialResizeState.HANDLED_INITIAL_SIZING;
                }, DEFERRED_INITIAL_SHRINKING_LAYOUT_FROM_IME_DURATION_MS);
                return;
            } else if (mInitialResizeState == InitialResizeState.IGNORING_SHRINKING) {
                // The dimensions changed in an unexpected way (either by increasing height or
                // a change in width), so just mark the initial sizing as completed and accept
                // the new measurements and suppress the pending posted layout request.
                mInitialResizeState = InitialResizeState.HANDLED_INITIAL_SIZING;
            }
            notifyObserversIfViewportHeightChanged(availableViewportHeight);

            mWidthMeasureSpec = MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY);
            // Note(nagamani@vivaldi.com): Use MeasureSpec.AT_MOST when the address bar is at
            // bottom to anchor the suggestion drop down to the bottom
            int heightParam = shouldAnchorToBottom() ? MeasureSpec.AT_MOST : MeasureSpec.EXACTLY;
            mHeightMeasureSpec = MeasureSpec.makeMeasureSpec(availableViewportHeight,
                    mEmbedder.isTablet() ? MeasureSpec.AT_MOST : heightParam);
            super.onMeasure(mWidthMeasureSpec, mHeightMeasureSpec);
            if (mInitialResizeState == InitialResizeState.WAITING_FOR_FIRST_MEASURE) {
                mInitialResizeState = InitialResizeState.WAITING_FOR_SHRINKING;
            }

            // Note(nagamani@vivaldi.com):  Return the calculated margin value to properly anchor
            // the search engine suggestion layout
            if (mSearchEngineSuggestionCallback != null) {
                if (mAlignmentView != null)
                    layoutMargins.leftMargin = mAlignmentView.getLeft();
                layoutMargins.topMargin = anchorBottomRelativeToContent;
                layoutMargins.bottomMargin = getBottomControlsHeight();
                mSearchEngineSuggestionCallback.onResult(layoutMargins);
            }
        }
    }

    private int calculateAnchorBottomRelativeToContent() {
        View contentView =
                mEmbedder.getAnchorView().getRootView().findViewById(android.R.id.content);
        ViewUtils.getRelativeLayoutPosition(contentView, mAnchorView, mTempPosition);
        int anchorY = mTempPosition[1];
        return anchorY + mAnchorView.getMeasuredHeight();
    }

    private void maybeUpdateLayoutParams(int topMargin) {
        // Update the layout params to ensure the parent correctly positions the suggestions
        // under the anchor view.
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams != null && layoutParams instanceof ViewGroup.MarginLayoutParams) {
            // Note(david@vivaldi.com): We consider the bottomMargin when we can anchor to the
            // bottom.
            if (shouldAnchorToBottom()) {
                ((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin =
                        getBottomControlsHeight() + getSearchEngineSuggestionLayoutHeight();
            } else
            ((ViewGroup.MarginLayoutParams) layoutParams).topMargin = topMargin
                + getSearchEngineSuggestionLayoutHeight();
        }
    }

    private int calculateAvailableViewportHeight(int anchorBottomRelativeToContent) {
        // Note(david@vivaldi.com): We don't take the visible display frame into account when we
        // can anchor to the bottom.
        // Subtract the total vivaldi occupied height from the available viewport
        if (shouldAnchorToBottom())
            return anchorBottomRelativeToContent - getTotalOccupiedHeight() ;

        mEmbedder.getWindowDelegate().getWindowVisibleDisplayFrame(mTempRect);
        return mTempRect.height() - anchorBottomRelativeToContent
                - getSearchEngineSuggestionLayoutHeight();
    }

    private void notifyObserversIfViewportHeightChanged(int availableViewportHeight) {
        if (availableViewportHeight == mListViewMaxHeight) return;

        mListViewMaxHeight = availableViewportHeight;
        if (mHeightChangeListener != null) {
            PostTask.postTask(UiThreadTaskTraits.DEFAULT, () -> {
                // Detect if there was another change since this task posted.
                // This indicates a subsequent task being posted too.
                if (mListViewMaxHeight != availableViewportHeight) return;
                // Detect if the new height is the same as previously broadcasted.
                // The two checks (one above and one below) allow us to detect quick
                // A->B->A transitions and suppress the broadcasts.
                if (mLastBroadcastedListViewMaxHeight == availableViewportHeight) return;
                if (mHeightChangeListener == null) return;

                mHeightChangeListener.onResult(availableViewportHeight);
                mLastBroadcastedListViewMaxHeight = availableViewportHeight;
                // Vivaldi - Note(nagamani@vivaldi.com): Scroll to the first element for the
                // suggestions to be clearly visible after viewport height changes when reverse
                // search suggestion is enabled.
                if (shouldReverseSuggestionsList()) scrollToPosition(0);
            });
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        try (TraceEvent tracing = TraceEvent.scoped("OmniboxSuggestionsList.Layout");
                TimingMetric metric = SuggestionsMetrics.recordSuggestionListLayoutTime()) {
            super.onLayout(changed, l, t, r, b);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isShown()) return false;

        View selectedView = mAdapter.getSelectedView();
        if (selectedView != null && selectedView.onKeyDown(keyCode, event)) {
            return true;
        }

        int selectedPosition = mAdapter.getSelectedViewIndex();
        if (KeyNavigationUtil.isGoDown(event)) {
            return mAdapter.setSelectedViewIndex(selectedPosition + 1);
        } else if (KeyNavigationUtil.isGoUp(event)) {
            return mAdapter.setSelectedViewIndex(selectedPosition - 1);
        } else if (KeyNavigationUtil.isEnter(event)) {
            if (selectedView != null) return selectedView.performClick();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // Consume mouse events to ensure clicks do not bleed through to sibling views that
        // are obscured by the list.  crbug.com/968414
        int action = event.getActionMasked();
        boolean shouldIgnoreGenericMotionEvent =
                (event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0
                && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
                && (action == MotionEvent.ACTION_BUTTON_PRESS
                        || action == MotionEvent.ACTION_BUTTON_RELEASE);
        return shouldIgnoreGenericMotionEvent || super.onGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int eventType = ev.getActionMasked();
        if ((eventType == MotionEvent.ACTION_UP || eventType == MotionEvent.ACTION_DOWN)
                && mGestureObserver != null) {
            mGestureObserver.onGesture(eventType == MotionEvent.ACTION_UP, ev.getEventTime());
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Sets the embedder for the list view.
     * @param embedder the embedder of this list.
     */
    public void setEmbedder(@NonNull OmniboxSuggestionsDropdownEmbedder embedder) {
        assert mEmbedder == null;
        mEmbedder = embedder;
        mAnchorView = mEmbedder.getAnchorView();
        // Prior to Android M, the contextual actions associated with the omnibox were anchored
        // to the top of the screen and not a floating copy/paste menu like on newer versions.
        // As a result of this, the toolbar is pushed down in these Android versions and we need
        // to montior those changes to update the positioning of the list.
        mAnchorViewLayoutListener = new OnGlobalLayoutListener() {
            private int mOffsetInWindow;
            private WindowInsets mWindowInsets;
            private final Rect mWindowRect = new Rect();

            @Override
            public void onGlobalLayout() {
                if (offsetInWindowChanged() || insetsHaveChanged()) {
                    requestLayout();
                }
            }

            private boolean offsetInWindowChanged() {
                int offsetInWindow = 0;
                View currentView = mAnchorView;
                while (true) {
                    offsetInWindow += currentView.getTop();
                    ViewParent parent = currentView.getParent();
                    if (parent == null || !(parent instanceof View)) break;
                    currentView = (View) parent;
                }
                boolean result = mOffsetInWindow != offsetInWindow;
                mOffsetInWindow = offsetInWindow;
                return result;
            }

            private boolean insetsHaveChanged() {
                boolean result = false;
                WindowInsets currentInsets = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    currentInsets = mAnchorView.getRootWindowInsets();
                    result = !currentInsets.equals(mWindowInsets);
                    mWindowInsets = currentInsets;
                } else {
                    mEmbedder.getWindowDelegate().getWindowVisibleDisplayFrame(mTempRect);
                    result = !mTempRect.equals(mWindowRect);
                    mWindowRect.set(mTempRect);
                }
                return result;
            }
        };

        mAlignmentView = mEmbedder.getAlignmentView();
        if (mAlignmentView != null) {
            mAlignmentViewLayoutListener = new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    adjustSidePadding();
                }
            };
        } else {
            mAlignmentViewLayoutListener = null;
        }
    }

    public void emitWindowContentChanged() {
        PostTask.postDelayedTask(UiThreadTaskTraits.DEFAULT, () -> {
            announceForAccessibility(getContext().getString(
                    R.string.accessibility_omnibox_suggested_items, mAdapter.getItemCount()));
        }, LIST_COMPOSITION_ACCESSIBILITY_ANNOUNCEMENT_DELAY_MS);

        // Vivaldi - Note(nagamani@vivaldi.com): Scroll to the first element for the
        // suggestions to be clearly visible when reverse search suggestion is enabled.
        if (shouldReverseSuggestionsList()) scrollToPosition(0);
    }

    private void adjustSidePadding() {
        if (mAlignmentView == null) return;

        ViewUtils.getRelativeLayoutPosition(mAnchorView, mAlignmentView, mTempPosition);
        setPadding(mTempPosition[0], getPaddingTop(),
                mAnchorView.getWidth() - mAlignmentView.getWidth() - mTempPosition[0],
                getPaddingBottom());
    }

    @VisibleForTesting
    public int getStandardBgColor() {
        return mStandardBgColor;
    }

    @VisibleForTesting
    public int getIncognitoBgColor() {
        return mIncognitoBgColor;
    }

    @VisibleForTesting
    SuggestionLayoutScrollListener getLayoutScrollListener() {
        return mLayoutScrollListener;
    }

    /** Vivaldi: Whether we should anchor the suggestion drop down to the bottom or not */
    private boolean shouldAnchorToBottom() {
        // Note(nagamani@vivaldi.com): We can't access SearchActivity anymore due to the dependency
        // issues. So we compare LocationBarLayout instance for the purpose.
        return isAddressBarAtBottom()
                && (mLocationBarLayout instanceof LocationBarPhone
                || mLocationBarLayout instanceof LocationBarTablet);
    }

    /** Vivaldi: Callback returns the anchor height for our search engine suggestion layout */
    public static void getAnchorMarginValue(Callback<LayoutMargins> callback) {
        mSearchEngineSuggestionCallback = callback;
    }

    /** Vivaldi: Returns the height of Search Engine suggestion layout if the option is
     * enabled or 0 otherwise */
    private int getSearchEngineSuggestionLayoutHeight() {
        if (showSearchEngineSuggestionBar())
            return (int) getResources().getDimension(R.dimen.search_engine_suggestion_view_height);
        // Note(nagamani@vivaldi.com): Search Engine suggestion layout shouldn't occupy any space if
        // the option is not enabled
        return 0;
    }

    /** Vivaldi: Returns the total occupied height on the screen (Like Address bar, Bottom toolbar,
     * Search engine suggestion bar) */
    private int getTotalOccupiedHeight() {
        int totalOccupiedHeight = mAnchorView.getMeasuredHeight()
                + (int) getResources().getDimension(R.dimen.tab_strip_height);
        // Note(nagamani@vivaldi.com): Search Engine suggestion layout height should be available
        // for the viewport when the option is not enabled
        if (!showSearchEngineSuggestionBar())
            totalOccupiedHeight -=
                    (int) getResources().getDimension(R.dimen.search_engine_suggestion_view_height);
        return totalOccupiedHeight;
    }

    /** Vivaldi: Returns the preference value */
    public boolean showSearchEngineSuggestionBar() {
        return SharedPreferencesManager.getInstance().readBoolean(
                "show_search_engine_suggestion", false);
    }

    /** Vivaldi: Returns the preference value */
    public boolean isAddressBarAtBottom() {
        return SharedPreferencesManager.getInstance().readBoolean(
                "address_bar_to_bottom", false);
    }

    /** Vivaldi: Returns the preference value */
    public boolean shouldReverseSuggestionsList() {
        return SharedPreferencesManager.getInstance().readBoolean(
                "reverse_search_suggestion", false) && shouldAnchorToBottom();
    }

    /** Vivaldi: Returns the bottom controls height including address bar, tab strip(if enabled) */
    public int getBottomControlsHeight() {
        int bottomControlsHeight = mAnchorView.getMeasuredHeight();
        boolean isTabStripOn = SharedPreferencesManager.getInstance().readBoolean(
                "show_tab_strip", true);
        boolean isTabStackToolbarOn = SharedPreferencesManager.getInstance().readBoolean(
                "tab_stack_toolbar_visible", false);
        boolean isTabStackActive = SharedPreferencesManager.getInstance().readBoolean(
                "tab_stack_visible", false);
        if (isTabStripOn)
            bottomControlsHeight += (int) getResources().getDimension(R.dimen.tab_strip_height);
        if (isTabStackActive || isTabStackToolbarOn)
            bottomControlsHeight += (int) getResources().getDimension(R.dimen.tab_strip_height);
        return bottomControlsHeight;
    }

    /** Vivaldi: Helps acquiring instance of LocationBarLayout to check if the search is from widget */
    public void setLocationBarModel(ViewGroup locationBarLayout) {
        mLocationBarLayout = (LocationBarLayout)locationBarLayout;
        final LinearLayoutManager layoutManager = (LinearLayoutManager)getLayoutManager();
        // Note(nagamani@vivaldi.com): Reverse the list when reverse search suggestion is enabled
        // for better reachability of suggestions UI
        if (shouldReverseSuggestionsList() && layoutManager != null) {
            layoutManager.setReverseLayout(true);
            layoutManager.setStackFromEnd(true);
        }
    }
}
