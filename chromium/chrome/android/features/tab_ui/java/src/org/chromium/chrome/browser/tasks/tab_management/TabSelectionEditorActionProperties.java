// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;

import org.chromium.base.Callback;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel.ReadableIntPropertyKey;
import org.chromium.ui.modelutil.PropertyModel.WritableBooleanPropertyKey;
import org.chromium.ui.modelutil.PropertyModel.WritableIntPropertyKey;
import org.chromium.ui.modelutil.PropertyModel.WritableObjectPropertyKey;

import java.util.List;

/**
 * Properties of the {@link TabSelectionEditorAction}.
 */
public class TabSelectionEditorActionProperties {
    public static final ReadableIntPropertyKey MENU_ITEM_ID = new ReadableIntPropertyKey();
    public static final ReadableIntPropertyKey SHOW_MODE = new ReadableIntPropertyKey();
    public static final ReadableIntPropertyKey BUTTON_TYPE = new ReadableIntPropertyKey();
    public static final ReadableIntPropertyKey ICON_POSITION = new ReadableIntPropertyKey();

    public static final WritableIntPropertyKey TITLE_RESOURCE_ID = new WritableIntPropertyKey();
    public static final WritableBooleanPropertyKey TITLE_IS_PLURAL =
            new WritableBooleanPropertyKey();
    public static final WritableObjectPropertyKey<Integer> CONTENT_DESCRIPTION_RESOURCE_ID =
            new WritableObjectPropertyKey();
    public static final WritableObjectPropertyKey<String> TITLE = new WritableObjectPropertyKey();
    public static final WritableObjectPropertyKey<String> CONTENT_DESCRIPTION =
            new WritableObjectPropertyKey<>();
    public static final WritableObjectPropertyKey<Drawable> ICON =
            new WritableObjectPropertyKey<>(true);
    public static final WritableBooleanPropertyKey ENABLED = new WritableBooleanPropertyKey();
    public static final WritableIntPropertyKey ITEM_COUNT = new WritableIntPropertyKey();
    public static final WritableObjectPropertyKey<ColorStateList> TEXT_TINT =
            new WritableObjectPropertyKey<>();

    /**
     * Tint for the icon. This should be null if {@code SKIP_ICON_TINT} is true.
     */
    public static final WritableObjectPropertyKey<ColorStateList> ICON_TINT =
            new WritableObjectPropertyKey<>();

    /**
     * If true skip usage of the {@code ICON_TINT} property. Used if the icon tint is handled
     * directly by the {@link TabSelectionEditorAction}.
     */
    public static final WritableBooleanPropertyKey SKIP_ICON_TINT =
            new WritableBooleanPropertyKey();

    public static final WritableObjectPropertyKey<Runnable> ON_CLICK_LISTENER =
            new WritableObjectPropertyKey<>();
    public static final WritableBooleanPropertyKey SHOULD_DISMISS_MENU =
            new WritableBooleanPropertyKey();
    public static final WritableObjectPropertyKey<Callback<List<Integer>>>
            ON_SELECTION_STATE_CHANGE = new WritableObjectPropertyKey<>();
    public static final WritableObjectPropertyKey<Runnable> ON_SHOWN_IN_MENU =
            new WritableObjectPropertyKey<>();

    /**
     * Keys for the {@link TabSelectionEditorAction}.
     */
    public static final PropertyKey[] ACTION_KEYS = {MENU_ITEM_ID, SHOW_MODE, BUTTON_TYPE,
            ICON_POSITION, TITLE_RESOURCE_ID, TITLE_IS_PLURAL, CONTENT_DESCRIPTION_RESOURCE_ID,
            ICON, ENABLED, ITEM_COUNT, TEXT_TINT, ICON_TINT, SKIP_ICON_TINT, ON_CLICK_LISTENER,
            SHOULD_DISMISS_MENU, ON_SELECTION_STATE_CHANGE, ON_SHOWN_IN_MENU};

    /**
     * Keys for the {@link TabSelectionEditorMenuItem}.
     */
    public static final PropertyKey[] MENU_ITEM_KEYS = {MENU_ITEM_ID, TITLE, CONTENT_DESCRIPTION,
            ICON, ICON_TINT, ENABLED, ITEM_COUNT, ON_SHOWN_IN_MENU};
}
