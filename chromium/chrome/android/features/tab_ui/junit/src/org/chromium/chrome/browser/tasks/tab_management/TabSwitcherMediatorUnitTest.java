// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import org.chromium.base.UserDataHost;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.UmaRecorderHolder;
import org.chromium.base.supplier.ObservableSupplierImpl;
import org.chromium.base.supplier.OneshotSupplierImpl;
import org.chromium.base.test.BaseRobolectricTestRunner;
import org.chromium.chrome.browser.browser_controls.BrowserControlsStateProvider;
import org.chromium.chrome.browser.compositor.CompositorViewHolder;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.incognito.reauth.IncognitoReauthController;
import org.chromium.chrome.browser.incognito.reauth.IncognitoReauthManager;
import org.chromium.chrome.browser.multiwindow.MultiWindowModeStateDispatcher;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelFilter;
import org.chromium.chrome.browser.tabmodel.TabModelFilterProvider;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorImpl;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.tasks.tab_management.TabSwitcher.TabSwitcherViewObserver;
import org.chromium.chrome.tab_ui.R;
import org.chromium.chrome.test.util.browser.Features;
import org.chromium.chrome.test.util.browser.Features.DisableFeatures;
import org.chromium.chrome.test.util.browser.Features.EnableFeatures;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.modelutil.PropertyObservable;
import org.chromium.url.GURL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link TabSwitcherMediator}.
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "ArraysAsListWithZeroOrOneArgument", "unchecked"})
@RunWith(BaseRobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.LEGACY)
@DisableFeatures(
        {ChromeFeatureList.TAB_SWITCHER_ON_RETURN, ChromeFeatureList.START_SURFACE_ANDROID})
@EnableFeatures({ChromeFeatureList.TAB_GRID_LAYOUT_ANDROID, ChromeFeatureList.TAB_TO_GTS_ANIMATION})
public class TabSwitcherMediatorUnitTest {
    @Rule
    public TestRule mProcessor = new Features.JUnitProcessor();

    private static final int CONTROL_HEIGHT_DEFAULT = 56;
    private static final int CONTROL_HEIGHT_MODIFIED = 0;
    private static final int CONTROL_HEIGHT_INCREASED = 76;

    private static final String TAB1_TITLE = "Tab1";
    private static final String TAB2_TITLE = "Tab2";
    private static final String TAB3_TITLE = "Tab3";
    private static final String TAB4_TITLE = "Tab4";
    private static final int TAB1_ID = 456;
    private static final int TAB2_ID = 789;
    private static final int TAB3_ID = 123;
    private static final int TAB4_ID = 357;

    private final OneshotSupplierImpl<IncognitoReauthController>
            mIncognitoReauthControllerSupplier = new OneshotSupplierImpl<>();

    @Mock
    TabSwitcherMediator.ResetHandler mResetHandler;
    @Mock
    TabContentManager mTabContentManager;
    @Mock
    TabModelSelectorImpl mTabModelSelector;
    @Mock
    TabModel mTabModel;
    @Mock
    TabModelFilter mTabModelFilter;
    @Mock
    TabModelFilterProvider mTabModelFilterProvider;
    @Mock
    Context mContext;
    @Mock
    Resources mResources;
    @Mock
    BrowserControlsStateProvider mBrowserControlsStateProvider;
    @Mock
    PropertyObservable.PropertyObserver<PropertyKey> mPropertyObserver;
    @Mock
    TabSwitcherViewObserver mTabSwitcherViewObserver;
    @Mock
    CompositorViewHolder mCompositorViewHolder;
    @Mock
    Layout mLayout;
    @Mock
    TabGridDialogMediator.DialogController mTabGridDialogController;
    @Mock
    TabSwitcherMediator.MessageItemsController mMessageItemsController;
    @Mock
    TabSwitcherMediator.PriceWelcomeMessageController mPriceWelcomeMessageController;
    @Mock
    MultiWindowModeStateDispatcher mMultiWindowModeStateDispatcher;
    @Mock
    PriceMessageService mPriceMessageService;
    @Mock
    IncognitoReauthController mIncognitoReauthController;

    @Captor
    ArgumentCaptor<TabModelObserver> mTabModelObserverCaptor;
    @Captor
    ArgumentCaptor<TabModelSelectorObserver> mTabModelSelectorObserverCaptor;
    @Captor
    private ArgumentCaptor<BrowserControlsStateProvider.Observer>
            mBrowserControlsStateProviderObserverCaptor;
    @Captor
    ArgumentCaptor<MultiWindowModeStateDispatcher.MultiWindowModeObserver>
            mMultiWindowModeObserverCaptor;
    @Captor
    private ArgumentCaptor<IncognitoReauthManager.IncognitoReauthCallback>
            mIncognitoReauthCallbackArgumentCaptor;

    private Tab mTab1;
    private Tab mTab2;
    private Tab mTab3;
    private TabSwitcherMediator mMediator;
    private PropertyModel mModel;

    @Before
    public void setUp() {
        UmaRecorderHolder.resetForTesting();

        MockitoAnnotations.initMocks(this);

        mTab1 = prepareTab(TAB1_ID, TAB1_TITLE);
        mTab2 = prepareTab(TAB2_ID, TAB2_TITLE);
        mTab3 = prepareTab(TAB3_ID, TAB3_TITLE);

        List<TabModel> tabModelList = new ArrayList<>();
        tabModelList.add(mTabModel);

        doNothing()
                .when(mTabContentManager)
                .getTabThumbnailWithCallback(anyInt(), any(), any(), anyBoolean(), anyBoolean());
        doReturn(mResources).when(mContext).getResources();

        doReturn(mTabModel).when(mTabModelSelector).getCurrentModel();
        doReturn(tabModelList).when(mTabModelSelector).getModels();
        doNothing().when(mTabModelSelector).addObserver(mTabModelSelectorObserverCaptor.capture());
        doReturn(mTabModelFilterProvider).when(mTabModelSelector).getTabModelFilterProvider();
        doReturn(mTabModelFilter).when(mTabModelFilterProvider).getCurrentTabModelFilter();

        doNothing()
                .when(mTabModelFilterProvider)
                .addTabModelFilterObserver(mTabModelObserverCaptor.capture());
        doReturn(mTab1).when(mTabModelFilter).getTabAt(0);
        doReturn(mTab2).when(mTabModelFilter).getTabAt(1);
        doReturn(mTab3).when(mTabModelFilter).getTabAt(2);
        doReturn(false).when(mTabModelFilter).isIncognito();
        doReturn(2).when(mTabModelFilter).index();
        doReturn(3).when(mTabModelFilter).getCount();

        doReturn(2).when(mTabModel).index();
        doReturn(3).when(mTabModel).getCount();
        doReturn(mTab1).when(mTabModel).getTabAt(0);
        doReturn(mTab2).when(mTabModel).getTabAt(1);
        doReturn(mTab3).when(mTabModel).getTabAt(2);

        doReturn(CONTROL_HEIGHT_DEFAULT)
                .when(mBrowserControlsStateProvider)
                .getBottomControlsHeight();
        doReturn(CONTROL_HEIGHT_DEFAULT).when(mBrowserControlsStateProvider).getTopControlsHeight();
        doReturn(CONTROL_HEIGHT_DEFAULT).when(mBrowserControlsStateProvider).getContentOffset();
        doNothing()
                .when(mBrowserControlsStateProvider)
                .addObserver(mBrowserControlsStateProviderObserverCaptor.capture());
        doReturn(true)
                .when(mMultiWindowModeStateDispatcher)
                .addObserver(mMultiWindowModeObserverCaptor.capture());
        TabSelectionEditorCoordinator.TabSelectionEditorController controller =
                mock(TabSelectionEditorCoordinator.TabSelectionEditorController.class);
        doReturn(new ObservableSupplierImpl<Boolean>())
                .when(controller)
                .getHandleBackPressChangedSupplier();
        doReturn(new ObservableSupplierImpl<Boolean>())
                .when(mTabGridDialogController)
                .getHandleBackPressChangedSupplier();
        when(mIncognitoReauthController.isIncognitoReauthPending()).thenReturn(false);
        mIncognitoReauthControllerSupplier.set(mIncognitoReauthController);

        mModel = new PropertyModel(TabListContainerProperties.ALL_KEYS);
        mModel.addObserver(mPropertyObserver);
        mMediator = new TabSwitcherMediator(mContext, mResetHandler, mModel, mTabModelSelector,
                mBrowserControlsStateProvider, mCompositorViewHolder, null, mMessageItemsController,
                mPriceWelcomeMessageController, mMultiWindowModeStateDispatcher,
                TabListCoordinator.TabListMode.GRID, mIncognitoReauthControllerSupplier);

        mMediator.initWithNative(controller, null);
        mMediator.addTabSwitcherViewObserver(mTabSwitcherViewObserver);
        mMediator.setOnTabSelectingListener(mLayout::onTabSelecting);
        verify(mIncognitoReauthController, times(1))
                .addIncognitoReauthCallback(mIncognitoReauthCallbackArgumentCaptor.capture());
    }

    @Test
    public void initializesWithCurrentModelOnCreation() {
        initAndAssertAllProperties();
    }

    @Test
    public void showsWithAnimation() {
        initAndAssertAllProperties();
        mMediator.showTabSwitcherView(true);

        assertThat(
                mModel.get(TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES), equalTo(true));
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));
        assertThat(mMediator.overviewVisible(), equalTo(true));
    }

    @Test
    public void showsWithoutAnimation() {
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();
        initAndAssertAllProperties();
        mMediator.showTabSwitcherView(false);

        InOrder inOrder = inOrder(mPropertyObserver, mPropertyObserver);
        inOrder.verify(mPropertyObserver)
                .onPropertyChanged(mModel, TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES);
        inOrder.verify(mPropertyObserver)
                .onPropertyChanged(mModel, TabListContainerProperties.IS_VISIBLE);
        inOrder.verify(mPropertyObserver)
                .onPropertyChanged(mModel, TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES);

        assertThat(
                mModel.get(TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES), equalTo(true));
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));
        assertThat(mMediator.overviewVisible(), equalTo(true));

        assertThat(RecordHistogram.getHistogramValueCountForTesting(
                           TabSwitcherMediator.TAB_COUNT_HISTOGRAM, 3),
                equalTo(1));
        assertThat(RecordHistogram.getHistogramValueCountForTesting(
                           TabSwitcherMediator.TAB_ENTRIES_HISTOGRAM, 3),
                equalTo(1));
    }

    @Test
    public void showsWithoutAnimation_withTabGroups() {
        doReturn(2).when(mTabModelFilter).getCount();
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();

        initAndAssertAllProperties();
        mMediator.showTabSwitcherView(false);

        InOrder inOrder = inOrder(mPropertyObserver, mPropertyObserver);
        inOrder.verify(mPropertyObserver)
                .onPropertyChanged(mModel, TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES);
        inOrder.verify(mPropertyObserver)
                .onPropertyChanged(mModel, TabListContainerProperties.IS_VISIBLE);
        inOrder.verify(mPropertyObserver)
                .onPropertyChanged(mModel, TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES);

        assertThat(
                mModel.get(TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES), equalTo(true));
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));
        assertThat(mMediator.overviewVisible(), equalTo(true));

        assertThat(RecordHistogram.getHistogramValueCountForTesting(
                           TabSwitcherMediator.TAB_COUNT_HISTOGRAM, 3),
                equalTo(1));
        assertThat(RecordHistogram.getHistogramValueCountForTesting(
                           TabSwitcherMediator.TAB_ENTRIES_HISTOGRAM, 2),
                equalTo(1));
    }

    @Test
    public void hidesWithAnimation() {
        initAndAssertAllProperties();
        mMediator.setTabGridDialogController(mTabGridDialogController);
        mMediator.showTabSwitcherView(true);

        assertThat(
                mModel.get(TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES), equalTo(true));
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));

        mMediator.hideTabSwitcherView(true);

        assertThat(
                mModel.get(TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES), equalTo(true));
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(false));
        assertThat(mMediator.overviewVisible(), equalTo(false));
        verify(mTabGridDialogController).hideDialog(eq(false));
    }

    @Test
    public void hidesWithoutAnimation() {
        initAndAssertAllProperties();
        mMediator.setTabGridDialogController(mTabGridDialogController);
        mMediator.showTabSwitcherView(true);

        assertThat(
                mModel.get(TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES), equalTo(true));
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));

        mMediator.hideTabSwitcherView(false);

        InOrder inOrder = inOrder(mPropertyObserver, mPropertyObserver);
        inOrder.verify(mPropertyObserver)
                .onPropertyChanged(mModel, TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES);
        inOrder.verify(mPropertyObserver)
                .onPropertyChanged(mModel, TabListContainerProperties.IS_VISIBLE);
        inOrder.verify(mPropertyObserver)
                .onPropertyChanged(mModel, TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES);

        assertThat(
                mModel.get(TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES), equalTo(true));
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(false));
        assertThat(mMediator.overviewVisible(), equalTo(false));
        verify(mTabGridDialogController).hideDialog(eq(false));
    }

    @Test
    public void beforeHideTabSwitcherView_NullController() {
        mMediator.prepareHideTabSwitcherView();
        verifyZeroInteractions(mTabGridDialogController);
    }

    @Test
    public void beforeHideTabSwitcherView_WithController() {
        mMediator.setTabGridDialogController(mTabGridDialogController);
        mMediator.prepareHideTabSwitcherView();
        verify(mTabGridDialogController).hideDialog(eq(false));
    }

    @Test
    public void startedShowingPropagatesToObservers() {
        initAndAssertAllProperties();
        mModel.get(TabListContainerProperties.VISIBILITY_LISTENER).startedShowing(true);
        verify(mTabSwitcherViewObserver).startedShowing();
    }

    @Test
    public void finishedShowingPropagatesToObservers() {
        initAndAssertAllProperties();
        mModel.get(TabListContainerProperties.VISIBILITY_LISTENER).finishedShowing();
        verify(mTabSwitcherViewObserver).finishedShowing();
    }

    @Test
    public void startedHidingPropagatesToObservers() {
        initAndAssertAllProperties();
        mModel.get(TabListContainerProperties.VISIBILITY_LISTENER).startedHiding(true);
        verify(mTabSwitcherViewObserver).startedHiding();
    }

    @Test
    public void finishedHidingPropagatesToObservers() {
        initAndAssertAllProperties();
        mModel.get(TabListContainerProperties.VISIBILITY_LISTENER).finishedHiding();
        verify(mTabSwitcherViewObserver).finishedHiding();
    }

    @Test
    public void resetsToNullAfterHidingFinishes() {
        initAndAssertAllProperties();
        mMediator.setSoftCleanupDelayForTesting(0);
        mMediator.setCleanupDelayForTesting(0);
        mMediator.postHiding();
        verify(mResetHandler).softCleanup();
        verify(mResetHandler).resetWithTabList(eq(null), eq(false), eq(false));
    }

    @Test
    public void resetsAfterNewTabModelSelected_DialogEnabled() {
        initAndAssertAllProperties();
        mModel.set(TabListContainerProperties.IS_VISIBLE, true);

        // Setup dialog reset handler. Default setup is that dialog handler is null.
        mMediator.setTabGridDialogController(mTabGridDialogController);

        doReturn(true).when(mTabModelFilter).isIncognito();
        mTabModelSelectorObserverCaptor.getValue().onTabModelSelected(mTabModel, null);
        verify(mResetHandler).resetWithTabList(eq(mTabModelFilter), eq(false), eq(false));
        verify(mTabGridDialogController).hideDialog(eq(false));
        assertThat(mModel.get(TabListContainerProperties.IS_INCOGNITO), equalTo(true));

        // Switching TabModels by itself shouldn't cause visibility changes.
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));
    }

    @Test
    public void resetsAfterNewTabModelSelected_DialogNotEnabled() {
        initAndAssertAllProperties();
        mModel.set(TabListContainerProperties.IS_VISIBLE, true);

        doReturn(true).when(mTabModelFilter).isIncognito();
        mTabModelSelectorObserverCaptor.getValue().onTabModelSelected(mTabModel, null);
        verify(mResetHandler).resetWithTabList(eq(mTabModelFilter), eq(false), eq(false));
        verify(mTabGridDialogController, never()).hideDialog(eq(false));
        assertThat(mModel.get(TabListContainerProperties.IS_INCOGNITO), equalTo(true));

        // Switching TabModels by itself shouldn't cause visibility changes.
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));
    }

    @Test
    public void noResetsAfterNewTabModelSelected_InvisibleGTS() {
        initAndAssertAllProperties();
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(false));

        // Setup dialog reset handler. Default setup is that dialog handler is null.
        mMediator.setTabGridDialogController(mTabGridDialogController);

        doReturn(true).when(mTabModelFilter).isIncognito();
        mTabModelSelectorObserverCaptor.getValue().onTabModelSelected(mTabModel, null);
        verify(mResetHandler, never()).resetWithTabList(any(), anyBoolean(), anyBoolean());
        verify(mTabGridDialogController).hideDialog(eq(false));
        assertThat(mModel.get(TabListContainerProperties.IS_INCOGNITO), equalTo(true));

        // Switching TabModels by itself shouldn't cause visibility changes.
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(false));
    }

    @Test
    public void scrollAfterNewTabModelSelected() {
        initAndAssertAllProperties();
        mModel.set(TabListContainerProperties.IS_VISIBLE, true);
        TabModel incognitoTabModel = mock(TabModel.class);

        doReturn(0).when(mTabModelFilter).index();
        mTabModelSelectorObserverCaptor.getValue().onTabModelSelected(incognitoTabModel, mTabModel);
        assertThat(mModel.get(TabListContainerProperties.INITIAL_SCROLL_INDEX), equalTo(0));

        doReturn(1).when(mTabModelFilter).index();
        mTabModelSelectorObserverCaptor.getValue().onTabModelSelected(incognitoTabModel, mTabModel);
        assertThat(mModel.get(TabListContainerProperties.INITIAL_SCROLL_INDEX), equalTo(0));

        doReturn(2).when(mTabModelFilter).index();
        mTabModelSelectorObserverCaptor.getValue().onTabModelSelected(incognitoTabModel, mTabModel);
        assertThat(mModel.get(TabListContainerProperties.INITIAL_SCROLL_INDEX), equalTo(0));

        doReturn(3).when(mTabModelFilter).index();
        mTabModelSelectorObserverCaptor.getValue().onTabModelSelected(incognitoTabModel, mTabModel);
        assertThat(mModel.get(TabListContainerProperties.INITIAL_SCROLL_INDEX), equalTo(1));
    }

    @Test
    public void updatesMarginWithBottomBarChanges() {
        initAndAssertAllProperties();

        assertThat(mModel.get(TabListContainerProperties.BOTTOM_CONTROLS_HEIGHT),
                equalTo(CONTROL_HEIGHT_DEFAULT));

        mBrowserControlsStateProviderObserverCaptor.getValue().onBottomControlsHeightChanged(
                CONTROL_HEIGHT_MODIFIED, 0);
        assertThat(mModel.get(TabListContainerProperties.BOTTOM_CONTROLS_HEIGHT),
                equalTo(CONTROL_HEIGHT_MODIFIED));
    }

    @Test
    public void hidesWhenATabIsSelected() {
        initAndAssertAllProperties();
        mMediator.showTabSwitcherView(true);
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();

        mTabModelObserverCaptor.getValue().didSelectTab(mTab1, TabSelectionType.FROM_USER, TAB3_ID);

        verify(mLayout).onTabSelecting(anyLong(), eq(TAB1_ID));
    }

    @Test
    public void doesNotHideWhenSelectedTabChangedDueToTabClosure() {
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();
        initAndAssertAllProperties();
        mMediator.showTabSwitcherView(true);
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));

        doReturn(true).when(mTab3).isClosing();
        mTabModelObserverCaptor.getValue().didSelectTab(
                mTab1, TabSelectionType.FROM_CLOSE, TAB3_ID);
        verify(mLayout, never()).onTabSelecting(anyLong(), anyInt());

        mTabModelObserverCaptor.getValue().didSelectTab(mTab1, TabSelectionType.FROM_USER, TAB3_ID);
        verify(mLayout).onTabSelecting(anyLong(), eq(TAB1_ID));
    }

    @Test
    public void doesNotHideWhenSelectedTabChangedDueToUndoTabClosure() {
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();
        initAndAssertAllProperties();
        mMediator.showTabSwitcherView(true);
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));

        doReturn(true).when(mTab3).isClosing();
        mTabModelObserverCaptor.getValue().didSelectTab(mTab1, TabSelectionType.FROM_UNDO, TAB3_ID);
        verify(mLayout, never()).onTabSelecting(anyLong(), anyInt());

        mTabModelObserverCaptor.getValue().didSelectTab(mTab1, TabSelectionType.FROM_USER, TAB3_ID);
        verify(mLayout).onTabSelecting(anyLong(), eq(TAB1_ID));
    }

    @Test
    public void doesNotHideWhenSelectedTabChangedDueToModelChange() {
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();
        initAndAssertAllProperties();
        mMediator.showTabSwitcherView(true);
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));

        mTabModelSelectorObserverCaptor.getValue().onTabModelSelected(mTabModel, null);
        mTabModelObserverCaptor.getValue().didSelectTab(mTab1, TabSelectionType.FROM_USER, TAB3_ID);
        verify(mLayout, never()).onTabSelecting(anyLong(), anyInt());

        mTabModelObserverCaptor.getValue().didSelectTab(mTab1, TabSelectionType.FROM_USER, TAB3_ID);
        verify(mLayout).onTabSelecting(anyLong(), eq(TAB1_ID));
    }

    @Test
    public void updatesResetHandlerOnRestoreCompleted() {
        initAndAssertAllProperties();
        mMediator.showTabSwitcherView(true);
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));

        mTabModelObserverCaptor.getValue().restoreCompleted();

        // MRU will be false unless the start surface is enabled.
        verify(mResetHandler).resetWithTabList(mTabModelFilter, false, false);
    }

    @Test
    public void setInitialScrollIndexOnRestoreCompleted() {
        initAndAssertAllProperties();
        mMediator.showTabSwitcherView(true);
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(true));

        mModel.set(TabListContainerProperties.INITIAL_SCROLL_INDEX, 1);

        mTabModelObserverCaptor.getValue().restoreCompleted();
        assertThat(mModel.get(TabListContainerProperties.INITIAL_SCROLL_INDEX), equalTo(0));
    }

    @Test
    public void removeMessageItemsWhenCloseLastTab() {
        initAndAssertAllProperties();
        // Mock that mTab1 is not the only tab in the current tab model and it will be closed.
        doReturn(2).when(mTabModel).getCount();
        mTabModelObserverCaptor.getValue().willCloseTab(mTab1, false, true);
        verify(mMessageItemsController, never()).removeAllAppendedMessage();

        // Mock that mTab1 is the only tab in the current tab model and it will be closed.
        doReturn(1).when(mTabModel).getCount();
        mTabModelObserverCaptor.getValue().willCloseTab(mTab1, false, true);
        verify(mMessageItemsController).removeAllAppendedMessage();
    }

    @Test
    public void restoreMessageItemsWhenUndoLastTabClosure() {
        initAndAssertAllProperties();
        // Mock that mTab1 was not the only tab in the current tab model and its closure will be
        // undone.
        doReturn(2).when(mTabModel).getCount();
        mTabModelObserverCaptor.getValue().tabClosureUndone(mTab1);
        verify(mMessageItemsController, never()).restoreAllAppendedMessage();

        // Mock that mTab1 was the only tab in the current tab model and its closure will be undone.
        doReturn(1).when(mTabModel).getCount();
        mTabModelObserverCaptor.getValue().tabClosureUndone(mTab1);
        verify(mMessageItemsController).restoreAllAppendedMessage();
    }

    @Test
    public void removePriceWelcomeMessageWhenCloseBindingTab() {
        mMediator.setPriceMessageService(mPriceMessageService);

        doReturn(1).when(mTabModel).getCount();
        doReturn(TAB1_ID).when(mPriceMessageService).getBindingTabId();
        mTabModelObserverCaptor.getValue().willCloseTab(mTab1, false, true);
        verify(mPriceWelcomeMessageController, times(0)).removePriceWelcomeMessage();

        doReturn(2).when(mTabModel).getCount();
        doReturn(TAB2_ID).when(mPriceMessageService).getBindingTabId();
        mTabModelObserverCaptor.getValue().willCloseTab(mTab1, false, true);
        verify(mPriceWelcomeMessageController, times(0)).removePriceWelcomeMessage();

        doReturn(2).when(mTabModel).getCount();
        doReturn(TAB1_ID).when(mPriceMessageService).getBindingTabId();
        mTabModelObserverCaptor.getValue().willCloseTab(mTab1, false, true);
        verify(mPriceWelcomeMessageController, times(1)).removePriceWelcomeMessage();
    }

    @Test
    public void restorePriceWelcomeMessageWhenUndoBindingTabClosure() {
        mMediator.setPriceMessageService(mPriceMessageService);

        doReturn(1).when(mTabModel).getCount();
        doReturn(TAB1_ID).when(mPriceMessageService).getBindingTabId();
        mTabModelObserverCaptor.getValue().tabClosureUndone(mTab1);
        verify(mPriceWelcomeMessageController, times(1)).restorePriceWelcomeMessage();

        doReturn(2).when(mTabModel).getCount();
        doReturn(TAB2_ID).when(mPriceMessageService).getBindingTabId();
        mTabModelObserverCaptor.getValue().tabClosureUndone(mTab1);
        verify(mPriceWelcomeMessageController, times(1)).restorePriceWelcomeMessage();
    }

    @Test
    public void invalidatePriceWelcomeMessageWhenBindingTabClosureCommitted() {
        mMediator.setPriceMessageService(mPriceMessageService);

        doReturn(TAB2_ID).when(mPriceMessageService).getBindingTabId();
        mTabModelObserverCaptor.getValue().tabClosureCommitted(mTab1);
        verify(mPriceMessageService, times(0)).invalidateMessage();

        doReturn(TAB1_ID).when(mPriceMessageService).getBindingTabId();
        mTabModelObserverCaptor.getValue().tabClosureCommitted(mTab1);
        verify(mPriceMessageService, times(1)).invalidateMessage();
    }

    @Test
    public void testScrollToTab() {
        initAndAssertAllProperties();
        mMediator.scrollToTab(0);
        assertThat(mModel.get(TabListContainerProperties.INITIAL_SCROLL_INDEX), equalTo(0));
        mMediator.scrollToTab(1);
        assertThat(mModel.get(TabListContainerProperties.INITIAL_SCROLL_INDEX), equalTo(1));
    }

    @Test
    public void showOverviewDoesNotUpdateResetHandlerBeforeRestoreCompleted() {
        initAndAssertAllProperties();
        doReturn(false).when(mTabModelSelector).isTabStateInitialized();
        mMediator.showTabSwitcherView(true);

        // MRU will be false unless the start surface is enabled.
        verify(mResetHandler, never()).resetWithTabList(mTabModelFilter, true, false);
    }

    @Test
    public void prepareOverviewDoesNotUpdateResetHandlerBeforeRestoreCompleted() {
        initAndAssertAllProperties();
        doReturn(false).when(mTabModelSelector).isTabStateInitialized();
        mMediator.prepareTabSwitcherView();

        // MRU will be false unless the start surface is enabled.
        verify(mResetHandler, never()).resetWithTabList(mTabModelFilter, false, false);
    }

    @Test
    public void showOverviewUpdatesResetHandlerAfterRestoreCompleted() {
        initAndAssertAllProperties();
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();

        mMediator.showTabSwitcherView(true);

        // MRU will be false unless the start surface is enabled.
        verify(mResetHandler).resetWithTabList(mTabModelFilter, true, false);
    }

    @Test
    public void prepareOverviewUpdatesResetHandlerAfterRestoreCompleted() {
        initAndAssertAllProperties();
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();

        mMediator.prepareTabSwitcherView();

        // MRU will be false unless the start surface is enabled.
        verify(mResetHandler).resetWithTabList(mTabModelFilter, false, false);
    }

    @Test
    public void prepareOverviewSetsInitialScrollIndexAfterRestoreCompleted() {
        initAndAssertAllProperties();
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();

        mModel.set(TabListContainerProperties.INITIAL_SCROLL_INDEX, 1);

        mMediator.prepareTabSwitcherView();
        assertThat(mModel.get(TabListContainerProperties.INITIAL_SCROLL_INDEX), equalTo(0));
    }

    @Test
    @Features.EnableFeatures({ChromeFeatureList.TAB_GROUPS_ANDROID})
    public void openDialogButton_SingleTab() {
        mMediator.setTabGridDialogController(mTabGridDialogController);
        // Mock that tab 1 is a single tab.
        doReturn(new ArrayList<>(Arrays.asList(mTab1)))
                .when(mTabModelFilter)
                .getRelatedTabList(TAB1_ID);
        assertThat(mMediator.openTabGridDialog(mTab1), equalTo(null));
    }

    @Test
    @Features.EnableFeatures({ChromeFeatureList.TAB_GROUPS_ANDROID})
    public void openDialogButton_TabGroup_NotEmpty() {
        mMediator.setTabGridDialogController(mTabGridDialogController);
        // Set up a tab group.
        Tab newTab = prepareTab(TAB4_ID, TAB4_TITLE);
        List<Tab> tabs = new ArrayList<>(Arrays.asList(mTab1, newTab));
        doReturn(tabs).when(mTabModelFilter).getRelatedTabList(TAB1_ID);

        TabListMediator.TabActionListener listener = mMediator.openTabGridDialog(mTab1);
        assertThat(listener, notNullValue());

        listener.run(TAB1_ID);
        verify(mTabGridDialogController).resetWithListOfTabs(eq(tabs));
    }

    @Test
    @Features.EnableFeatures({ChromeFeatureList.TAB_GROUPS_ANDROID})
    public void openDialogButton_TabGroup_Empty() {
        mMediator.setTabGridDialogController(mTabGridDialogController);
        // Assume that due to tab model change, current group becomes empty in current model.
        doReturn(new ArrayList<>()).when(mTabModelFilter).getRelatedTabList(TAB1_ID);

        TabListMediator.TabActionListener listener = mMediator.openTabGridDialog(mTab1);
        assertThat(listener, notNullValue());

        listener.run(TAB1_ID);
        verify(mTabGridDialogController).resetWithListOfTabs(eq(null));
    }

    @Test
    public void updatesPropertiesWithTopControlsChanges() {
        assertEquals("Wrong initial top margin.", CONTROL_HEIGHT_DEFAULT,
                mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals("Wrong initial shadow top offset", CONTROL_HEIGHT_DEFAULT,
                mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));

        // Change top controls height without animation.
        doReturn(CONTROL_HEIGHT_INCREASED)
                .when(mBrowserControlsStateProvider)
                .getTopControlsHeight();
        doReturn(CONTROL_HEIGHT_INCREASED).when(mBrowserControlsStateProvider).getContentOffset();
        mBrowserControlsStateProviderObserverCaptor.getValue().onTopControlsHeightChanged(
                CONTROL_HEIGHT_INCREASED, 0);
        mBrowserControlsStateProviderObserverCaptor.getValue().onControlsOffsetChanged(
                0, 0, 0, 0, false);

        assertEquals(
                "Top margin should be equal to top controls height if controls are fully shown.",
                CONTROL_HEIGHT_INCREASED, mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals("Shadow offset should follow the content offset.", CONTROL_HEIGHT_INCREASED,
                mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));

        // Change top controls height without animation.
        doReturn(CONTROL_HEIGHT_DEFAULT).when(mBrowserControlsStateProvider).getTopControlsHeight();
        doReturn(CONTROL_HEIGHT_DEFAULT).when(mBrowserControlsStateProvider).getContentOffset();
        mBrowserControlsStateProviderObserverCaptor.getValue().onTopControlsHeightChanged(
                CONTROL_HEIGHT_DEFAULT, 0);
        mBrowserControlsStateProviderObserverCaptor.getValue().onControlsOffsetChanged(
                0, 0, 0, 0, false);

        assertEquals("Top margin should be equal to top controls height if controls are at rest.",
                CONTROL_HEIGHT_DEFAULT, mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals("Shadow offset should follow the content offset.", CONTROL_HEIGHT_DEFAULT,
                mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));
    }

    @Test
    public void testTopControlsHeightAnimations() {
        assertEquals("Wrong initial top margin.", CONTROL_HEIGHT_DEFAULT,
                mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals("Wrong initial shadow top offset", CONTROL_HEIGHT_DEFAULT,
                mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));

        // Increase the height.
        doReturn(CONTROL_HEIGHT_INCREASED)
                .when(mBrowserControlsStateProvider)
                .getTopControlsHeight();
        mBrowserControlsStateProviderObserverCaptor.getValue().onTopControlsHeightChanged(
                CONTROL_HEIGHT_INCREASED, 20);

        assertEquals("Top margin shouldn't change until the animation starts.",
                CONTROL_HEIGHT_DEFAULT, mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals("Shadow offset should follow the content offset.", CONTROL_HEIGHT_DEFAULT,
                mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));

        // Animate by changing the content offset.
        for (int offset = CONTROL_HEIGHT_DEFAULT; offset < CONTROL_HEIGHT_INCREASED; offset += 5) {
            doReturn(offset).when(mBrowserControlsStateProvider).getContentOffset();
            mBrowserControlsStateProviderObserverCaptor.getValue().onControlsOffsetChanged(
                    offset - CONTROL_HEIGHT_INCREASED, offset - CONTROL_HEIGHT_DEFAULT, 0, 0,
                    false);

            assertEquals("Top margin should follow the content offset during the animation.",
                    offset, mModel.get(TabListContainerProperties.TOP_MARGIN));
            assertEquals("Shadow offset should follow the content offset.", offset,
                    mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));
        }

        // End the animation.
        doReturn(CONTROL_HEIGHT_INCREASED).when(mBrowserControlsStateProvider).getContentOffset();
        mBrowserControlsStateProviderObserverCaptor.getValue().onControlsOffsetChanged(
                0, 20, 0, 0, false);

        assertEquals("Top margin should be updated to the new height when the animation ends.",
                CONTROL_HEIGHT_INCREASED, mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals("Shadow offset should follow the content offset.", CONTROL_HEIGHT_INCREASED,
                mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));

        // Now decrease the height, back to the default.
        doReturn(CONTROL_HEIGHT_DEFAULT).when(mBrowserControlsStateProvider).getTopControlsHeight();
        mBrowserControlsStateProviderObserverCaptor.getValue().onTopControlsHeightChanged(
                CONTROL_HEIGHT_DEFAULT, 0);

        assertEquals("Top margin shouldn't change until the animation starts.",
                CONTROL_HEIGHT_INCREASED, mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals("Shadow offset should follow the content offset.", CONTROL_HEIGHT_INCREASED,
                mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));

        // Animate by changing the content offset.
        for (int offset = CONTROL_HEIGHT_INCREASED; offset > CONTROL_HEIGHT_DEFAULT; offset -= 5) {
            doReturn(offset).when(mBrowserControlsStateProvider).getContentOffset();
            mBrowserControlsStateProviderObserverCaptor.getValue().onControlsOffsetChanged(
                    offset - CONTROL_HEIGHT_DEFAULT, CONTROL_HEIGHT_INCREASED - offset, 0, 0,
                    false);

            assertEquals("Top margin should follow the content offset during the animation.",
                    offset, mModel.get(TabListContainerProperties.TOP_MARGIN));
            assertEquals("Shadow offset should follow the content offset.", offset,
                    mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));
        }

        // End the animation.
        doReturn(CONTROL_HEIGHT_DEFAULT).when(mBrowserControlsStateProvider).getContentOffset();
        mBrowserControlsStateProviderObserverCaptor.getValue().onControlsOffsetChanged(
                0, 0, 0, 0, false);

        assertEquals("Top margin should be updated to the new height at the end of the animation.",
                CONTROL_HEIGHT_DEFAULT, mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals("Shadow offset should follow the content offset.", CONTROL_HEIGHT_DEFAULT,
                mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));
    }

    @Test
    @Features.EnableFeatures(ChromeFeatureList.START_SURFACE_ANDROID)
    public void updatesPropertiesWithTopControlsChanges_StartSurface() {
        assertEquals(0, mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals(0, mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));

        mBrowserControlsStateProviderObserverCaptor.getValue().onTopControlsHeightChanged(
                CONTROL_HEIGHT_INCREASED, 0);
        doReturn(CONTROL_HEIGHT_INCREASED).when(mBrowserControlsStateProvider).getContentOffset();
        mBrowserControlsStateProviderObserverCaptor.getValue().onControlsOffsetChanged(
                0, 0, 0, 0, false);
        assertEquals(0, mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals(0, mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));

        mBrowserControlsStateProviderObserverCaptor.getValue().onTopControlsHeightChanged(
                CONTROL_HEIGHT_DEFAULT, 0);
        doReturn(CONTROL_HEIGHT_DEFAULT).when(mBrowserControlsStateProvider).getContentOffset();
        mBrowserControlsStateProviderObserverCaptor.getValue().onControlsOffsetChanged(
                0, 0, 0, 0, false);
        assertEquals(0, mModel.get(TabListContainerProperties.TOP_MARGIN));
        assertEquals(0, mModel.get(TabListContainerProperties.SHADOW_TOP_OFFSET));
    }

    @Test
    public void updatesBottomPaddingOnlyInGridMode() {
        doReturn(16f).when(mResources).getDimension(R.dimen.tab_grid_bottom_padding);

        assertEquals(0, mModel.get(TabListContainerProperties.BOTTOM_PADDING));
        new TabSwitcherMediator(mContext, mResetHandler, mModel, mTabModelSelector,
                mBrowserControlsStateProvider, mCompositorViewHolder, null, mMessageItemsController,
                mPriceWelcomeMessageController, mMultiWindowModeStateDispatcher,
                TabListCoordinator.TabListMode.GRID, mIncognitoReauthControllerSupplier);
        assertEquals(16, mModel.get(TabListContainerProperties.BOTTOM_PADDING));

        mModel.set(TabListContainerProperties.BOTTOM_PADDING, 0);
        new TabSwitcherMediator(mContext, mResetHandler, mModel, mTabModelSelector,
                mBrowserControlsStateProvider, mCompositorViewHolder, null, mMessageItemsController,
                mPriceWelcomeMessageController, mMultiWindowModeStateDispatcher,
                TabListCoordinator.TabListMode.STRIP, mIncognitoReauthControllerSupplier);
        assertEquals(0, mModel.get(TabListContainerProperties.BOTTOM_PADDING));
    }

    @Test
    public void enterMultiWindowMode() {
        initAndAssertAllProperties();

        mMultiWindowModeObserverCaptor.getValue().onMultiWindowModeChanged(true);

        verify(mMessageItemsController).removeAllAppendedMessage();
    }

    @Test
    public void exitMultiWindowMode() {
        initAndAssertAllProperties();

        mMultiWindowModeObserverCaptor.getValue().onMultiWindowModeChanged(false);

        verify(mMessageItemsController).restoreAllAppendedMessage();
    }

    @Test
    @EnableFeatures({ChromeFeatureList.BACK_GESTURE_REFACTOR})
    public void testBackPress() {
        initAndAssertAllProperties();
        Assert.assertFalse(mMediator.shouldInterceptBackPress());

        mMediator.setTabGridDialogController(mTabGridDialogController);
        doReturn(true).when(mTabGridDialogController).isVisible();
        Assert.assertTrue("Should intercept back press if tab grid dialog is visible",
                mMediator.shouldInterceptBackPress());

        doReturn(false).when(mTabGridDialogController).isVisible();
        mMediator.showTabSwitcherView(false);
        doReturn(mTab1).when(mTabModelSelector).getCurrentTab();
        Assert.assertTrue(mMediator.shouldInterceptBackPress());
    }

    @Test
    public void testOnTabModelSelected_NewModelIncognito_ReauthPending_ClearsTabList() {
        initAndAssertAllProperties();
        mModel.set(TabListContainerProperties.IS_VISIBLE, true);
        doReturn(true).when(mTabModelFilter).isIncognito();
        doReturn(true).when(mTabModelSelector).isIncognitoSelected();
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();
        doReturn(true).when(mIncognitoReauthController).isIncognitoReauthPending();
        doReturn(true).when(mTabModel).isIncognito();

        mTabModelSelectorObserverCaptor.getValue().onTabModelSelected(mTabModel, null);
        verify(mResetHandler, times(1)).resetWithTabList(eq(null), eq(false), eq(false));
    }

    @Test
    public void testOnTabModelSelected_NewModelIncognito_ReauthSuccessful_RestoresTabList() {
        initAndAssertAllProperties();
        mModel.set(TabListContainerProperties.IS_VISIBLE, true);
        doReturn(true).when(mTabModelFilter).isIncognito();
        doReturn(true).when(mTabModelSelector).isIncognitoSelected();
        doReturn(true).when(mTabModelSelector).isTabStateInitialized();
        doReturn(true).when(mIncognitoReauthController).isIncognitoReauthPending();
        doReturn(true).when(mTabModel).isIncognito();

        mTabModelSelectorObserverCaptor.getValue().onTabModelSelected(mTabModel, null);
        verify(mResetHandler, times(1)).resetWithTabList(eq(null), eq(false), eq(false));

        // Mock that the re-auth was successful.
        doReturn(false).when(mIncognitoReauthController).isIncognitoReauthPending();
        mIncognitoReauthCallbackArgumentCaptor.getValue().onIncognitoReauthSuccess();

        // Verify we reset the tab-list with the current tab model filter.
        verify(mResetHandler, times(1)).resetWithTabList(eq(mTabModelFilter), eq(false), eq(false));

        // Check we don't call reset handler on other cases
        mIncognitoReauthCallbackArgumentCaptor.getValue().onIncognitoReauthNotPossible();
        mIncognitoReauthCallbackArgumentCaptor.getValue().onIncognitoReauthFailure();
        // Verify we don't reset the tab-list with the current tab model filter again.
        verifyNoMoreInteractions(mResetHandler);
    }

    private void initAndAssertAllProperties() {
        assertThat(mModel.get(TabListContainerProperties.VISIBILITY_LISTENER),
                instanceOf(TabSwitcherMediator.class));
        assertThat(
                mModel.get(TabListContainerProperties.ANIMATE_VISIBILITY_CHANGES), equalTo(true));
        assertThat(mModel.get(TabListContainerProperties.IS_INCOGNITO),
                equalTo(mTabModel.isIncognito()));
        assertThat(mModel.get(TabListContainerProperties.IS_VISIBLE), equalTo(false));
        assertThat(
                mModel.get(TabListContainerProperties.TOP_MARGIN), equalTo(CONTROL_HEIGHT_DEFAULT));
        assertThat(mModel.get(TabListContainerProperties.BOTTOM_CONTROLS_HEIGHT),
                equalTo(CONTROL_HEIGHT_DEFAULT));
    }

    private Tab prepareTab(int id, String title) {
        Tab tab = mock(Tab.class);
        when(tab.getView()).thenReturn(mock(View.class));
        when(tab.getUserDataHost()).thenReturn(new UserDataHost());
        doReturn(id).when(tab).getId();
        doReturn(GURL.emptyGURL()).when(tab).getUrl();
        doReturn(title).when(tab).getTitle();
        doReturn(false).when(tab).isClosing();
        return tab;
    }
}
