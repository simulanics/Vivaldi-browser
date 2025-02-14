// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.widget.TextViewCompat;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.tasks.tab_management.TabSelectionEditorAction.ButtonType;
import org.chromium.chrome.browser.tasks.tab_management.TabSelectionEditorAction.IconPosition;
import org.chromium.chrome.browser.tasks.tab_management.TabSelectionEditorAction.ShowMode;
import org.chromium.chrome.tab_ui.R;
import org.chromium.ui.modelutil.MVCListAdapter.ListItem;

import java.util.List;

/**
 * Holds the {@code mActionView} and {@link ListItem} for an item in the {@link
 * TabSelectionEditorMenu}.
 */
public class TabSelectionEditorMenuItem {
    private Context mContext;

    private int mMenuId;
    private ListItem mListItem;
    private Button mActionView;
    private boolean mShowText;
    private boolean mShowIcon;
    private boolean mEnabled;
    private boolean mShouldDismissMenu;
    private boolean mActionViewShowing;
    private ColorStateList mIconTint;

    private Runnable mOnClickRunnable;
    private Callback<List<Integer>> mOnSelectionStateChange;

    /**
     * @param context for loading resources.
     */
    TabSelectionEditorMenuItem(Context context, ListItem listItem) {
        mContext = context;
        mListItem = listItem;
    }

    /**
     * Initializes the {@code mActionView} if applicable.
     * @param showMode whether to show the action view.
     * @param buttonType the button layout of the action view.
     */
    public void initActionView(@ShowMode int showMode, @ButtonType int buttonType) {
        mShowText = buttonType == ButtonType.TEXT || buttonType == ButtonType.ICON_AND_TEXT;
        mShowIcon = buttonType == ButtonType.ICON || buttonType == ButtonType.ICON_AND_TEXT;

        if ((!mShowText && !mShowIcon) || showMode == ShowMode.MENU_ONLY) return;

        mActionView = (Button) LayoutInflater.from(mContext).inflate(
                R.layout.tab_selection_editor_action_view, null);
        mActionView.setId(mListItem.model.get(TabSelectionEditorActionProperties.MENU_ITEM_ID));
        if (mShowIcon && !mShowText) {
            mActionView.setCompoundDrawablePadding(0);
        }
    }

    public View getActionView() {
        return mActionView;
    }

    public ListItem getListItem() {
        return mListItem;
    }

    /**
     * Set the title in the menu and ActionView.
     * @param titleResourceId Resource ID of the title.
     * @param itemCount current item count. -1 means the title is not plural.
     */
    public void setTitle(int titleResourceId, int itemCount) {
        String title;
        if (itemCount >= 0) {
            title = mContext.getResources().getQuantityString(
                    titleResourceId, itemCount, itemCount);
        } else {
            title = mContext.getResources().getString(titleResourceId);
        }
        mListItem.model.set(TabSelectionEditorActionProperties.TITLE, title);
        if (mActionView != null) {
            if (mShowText) {
                mActionView.setText(title);
            } else {
                mActionView.setText("");
                mActionView.setMinWidth(0);
                mActionView.setMinimumWidth(0);
            }
        }
    }

    /**
     * Builds a content description for a plural number of items. Defaults to the title otherwise.
     * @param contentDescriptionResourceId for the plural string to use or null to use the title.
     * @param itemCount the count of items selected.
     */
    public void setContentDescription(
            @Nullable Integer contentDescriptionResourceId, int itemCount) {
        String contentDescription = null;
        if (contentDescriptionResourceId != null && itemCount > 0) {
            contentDescription = mContext.getResources().getQuantityString(
                    contentDescriptionResourceId, itemCount, itemCount);
        }
        mListItem.model.set(
                TabSelectionEditorActionProperties.CONTENT_DESCRIPTION, contentDescription);
        if (mActionView != null) {
            mActionView.setContentDescription(contentDescription);
        }
    }

    /**
     * Sets the icon for the action view and menu item.
     * @param iconPosition for the action view.
     * @param icon to display in the menu item or action view.
     */
    public void setIcon(@IconPosition int iconPosition, Drawable icon) {
        mListItem.model.set(TabSelectionEditorActionProperties.ICON, icon);
        if (mActionView != null && mShowIcon) {
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(mActionView,
                    iconPosition == IconPosition.START ? icon : null, null,
                    iconPosition == IconPosition.END ? icon : null, null);
        }
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        mListItem.model.set(TabSelectionEditorActionProperties.ENABLED, enabled);
        if (mActionView != null) {
            mActionView.setEnabled(enabled);
        }
    }

    public void setTextTint(ColorStateList colorStateList) {
        // mListItem uses the default text tint.
        if (mActionView != null) {
            mActionView.setTextColor(colorStateList);
        }
    }

    public void setIconTint(@Nullable ColorStateList colorStateList) {
        // A null colorStateList is used with TabSelectionEditorActionProperties.SKIP_ICON_TINT
        // = true to signal that a custom tint is used. Ignore null so that this custom tint is
        // not overridden.
        if (colorStateList == null) {
            mIconTint = null;
            mListItem.model.set(TabSelectionEditorActionProperties.ICON_TINT, null);
            return;
        }

        // mListItem uses the default icon tint whenever shown. Cache the tint to restore it when
        // the action view shown state is toggled.
        mListItem.model.set(TabSelectionEditorActionProperties.ICON_TINT,
                AppCompatResources.getColorStateList(
                        mContext, R.color.default_icon_color_secondary_tint_list));
        mIconTint = colorStateList;
        if (mActionView != null && mActionViewShowing) {
            TextViewCompat.setCompoundDrawableTintList(mActionView, colorStateList);
        }
    }

    public void setActionViewShowing(boolean actionViewShowing) {
        mActionViewShowing = actionViewShowing;
        if (mActionViewShowing) {
            // Ensure the drawable has the correct tint.
            setIconTint(mIconTint);
        }
    }

    public void setOnClickListener(Runnable runnable) {
        mOnClickRunnable = runnable;
        if (mActionView != null) {
            mActionView.setOnClickListener(v -> onClick());
        }
    }

    public void setShouldDismissMenu(boolean shouldDismissMenu) {
        mShouldDismissMenu = shouldDismissMenu;
    }

    public boolean shouldDismissMenu() {
        return mShouldDismissMenu;
    }

    public void setOnSelectionStateChange(Callback<List<Integer>> callback) {
        mOnSelectionStateChange = callback;
    }

    public void setOnShownInMenu(Runnable runnable) {
        mListItem.model.set(TabSelectionEditorActionProperties.ON_SHOWN_IN_MENU, runnable);
    }

    /**
     * Handler for click events on the menu item or action view.
     */
    public boolean onClick() {
        if (!mEnabled) return false;

        mOnClickRunnable.run();

        return true;
    }

    /**
     * Updates the {@link TabSelectionEditorAction} with the currently selected tabs.
     */
    public void onSelectionStateChange(List<Integer> tabIds) {
        mOnSelectionStateChange.onResult(tabIds);
    }

    private static int getShowAsAction(@ShowMode int showMode) {
        switch (showMode) {
            case ShowMode.MENU_ONLY:
                return MenuItem.SHOW_AS_ACTION_NEVER;
            case ShowMode.IF_ROOM:
                return MenuItem.SHOW_AS_ACTION_IF_ROOM;
            default:
                assert false;
                return MenuItem.SHOW_AS_ACTION_NEVER;
        }
    }
}
