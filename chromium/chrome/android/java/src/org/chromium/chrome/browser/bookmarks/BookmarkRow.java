// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import static org.chromium.components.browser_ui.widget.listmenu.BasicListMenu.buildMenuListItem;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.content.res.AppCompatResources;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.app.bookmarks.BookmarkAddEditFolderActivity;
import org.chromium.chrome.browser.app.bookmarks.BookmarkFolderSelectActivity;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkItem;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.components.browser_ui.widget.listmenu.BasicListMenu;
import org.chromium.components.browser_ui.widget.listmenu.ListMenu;
import org.chromium.components.browser_ui.widget.listmenu.ListMenuButton;
import org.chromium.components.browser_ui.widget.listmenu.ListMenuButton.PopupMenuShownListener;
import org.chromium.components.browser_ui.widget.listmenu.ListMenuButtonDelegate;
import org.chromium.components.browser_ui.widget.listmenu.ListMenuItemProperties;
import org.chromium.components.browser_ui.widget.selectable_list.SelectableItemView;
import org.chromium.components.browser_ui.widget.selectable_list.SelectableListUtils;
import org.chromium.ui.modelutil.MVCListAdapter.ModelList;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import org.chromium.chrome.browser.ChromeApplicationImpl;

/**
 * Common logic for bookmark and folder rows.
 */
public abstract class BookmarkRow
        extends SelectableItemView<BookmarkId> implements BookmarkUIObserver {
    protected ListMenuButton mMoreIcon;
    protected ImageView mDragHandle;
    protected BookmarkDelegate mDelegate;
    protected BookmarkId mBookmarkId;
    private boolean mIsAttachedToWindow;
    private PopupMenuShownListener mPopupListener;
    @Location
    private int mLocation;
    private boolean mFromFilterView;

    @IntDef({Location.TOP, Location.MIDDLE, Location.BOTTOM, Location.SOLO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Location {
        int TOP = 0;
        int MIDDLE = 1;
        int BOTTOM = 2;
        int SOLO = 3;
    }

    /**
     * Constructor for inflating from XML.
     */
    public BookmarkRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (BookmarkFeatures.isBookmarksVisualRefreshEnabled()) {
            enableVisualRefresh(getResources().getDimensionPixelSize(
                    BookmarkFeatures.isCompactBookmarksVisualRefreshEnabled()
                            ? R.dimen.list_item_v2_start_icon_width_compact
                            : R.dimen.list_item_v2_start_icon_width));
        }
    }

    /**
     * Sets the bookmark ID for this BookmarkRow and provides information about its location
     * within the list of bookmarks.
     *
     * @param bookmarkId The BookmarkId that this BookmarkRow now contains.
     * @param location   The location of this BookmarkRow.
     * @param fromFilterView The Bookmark is being displayed in a filter view, determines if the row
     *         is selectable.
     * @return The BookmarkItem corresponding to BookmarkId.
     */
    BookmarkItem setBookmarkId(
            BookmarkId bookmarkId, @Location int location, boolean fromFilterView) {
        mLocation = location;
        mBookmarkId = bookmarkId;
        mFromFilterView = fromFilterView;
        BookmarkItem bookmarkItem = mDelegate.getModel().getBookmarkById(bookmarkId);
        mMoreIcon.dismiss();
        SelectableListUtils.setContentDescriptionContext(getContext(), mMoreIcon,
                bookmarkItem.getTitle(), SelectableListUtils.ContentDescriptionSource.MENU_BUTTON);

        setChecked(isItemSelected());
        updateVisualState();

        super.setItem(bookmarkId);
        return bookmarkItem;
    }

    private void updateVisualState() {
        // This check is needed because it is possible for updateVisualState to be called between
        // onDelegateInitialized (SelectionDelegate triggers a redraw) and setBookmarkId. View is
        // not currently bound, so we can skip this for now. updateVisualState will run inside of
        // setBookmarkId.
        if (mBookmarkId == null) {
            return;
        }
        BookmarkItem bookmarkItem = mDelegate.getModel().getBookmarkById(mBookmarkId);
        // This check is needed because updateVisualState is called when the item has been deleted
        // in the model but not in the adapter. If we hit this if-block, the
        // item is about to be deleted, and we don't need to do anything.
        if (bookmarkItem == null) {
            return;
        }
        // TODO(jhimawan): Look into using cleanup(). Perhaps unhook the selection state observer?

        // If the visibility of the drag handle or more icon is not set later, it will be gone.
        mDragHandle.setVisibility(GONE);
        mMoreIcon.setVisibility(GONE);

        if (mDelegate.getDragStateDelegate().getDragActive()) {
            mDragHandle.setVisibility(
                    bookmarkItem.isReorderable() && !mFromFilterView ? VISIBLE : GONE);
            mDragHandle.setEnabled(isItemSelected());
        } else {
            mMoreIcon.setVisibility(bookmarkItem.isEditable() ? VISIBLE : GONE);
            mMoreIcon.setClickable(!isSelectionModeActive());
            mMoreIcon.setEnabled(mMoreIcon.isClickable());
            mMoreIcon.setImportantForAccessibility(mMoreIcon.isClickable()
                            ? IMPORTANT_FOR_ACCESSIBILITY_YES
                            : IMPORTANT_FOR_ACCESSIBILITY_NO);

            // Vivaldi - Display more icon for Reading list items
            if (ChromeApplicationImpl.isVivaldi()
                    && bookmarkItem.getId().getType() != BookmarkType.READING_LIST)
                mMoreIcon.setVisibility(View.GONE);
        }
    }

    /**
     * Sets the delegate to use to handle UI actions related to this view.
     *
     * @param delegate A {@link BookmarkDelegate} instance to handle all backend interaction.
     */
    public void onDelegateInitialized(BookmarkDelegate delegate) {
        super.setSelectionDelegate(delegate.getSelectionDelegate());
        mDelegate = delegate;
        if (mIsAttachedToWindow) initialize();
    }

    private void initialize() {
        mDelegate.addUIObserver(this);
        mPopupListener = () -> mDelegate.onBookmarkItemMenuOpened();
        mMoreIcon.addPopupListener(mPopupListener);
    }

    private void cleanup() {
        mMoreIcon.dismiss();
        mMoreIcon.removePopupListener(mPopupListener);
        if (mDelegate != null) mDelegate.removeUIObserver(this);
    }

    private ModelList getItems() {
        // Rebuild listItems, cause mLocation may be changed anytime.
        boolean canReorder = false;
        boolean canMove = false;
        BookmarkItem bookmarkItem = null;
        if (mDelegate != null && mDelegate.getModel() != null) {
            bookmarkItem = mDelegate.getModel().getBookmarkById(mBookmarkId);
            if (bookmarkItem != null) {
                // Reading list items can sometimes be movable (for type swapping purposes), but for
                // UI purposes they shouldn't be movable.
                canMove = BookmarkUtils.isMovable(bookmarkItem);
                canReorder = bookmarkItem.isReorderable() && !mFromFilterView;
            }
        }
        ModelList listItems = new ModelList();
        if (mBookmarkId.getType() == BookmarkType.READING_LIST) {
            // TODO(crbug.com/1269434): Add ability to mark an item as unread.
            if (bookmarkItem != null && !bookmarkItem.isRead()) {
                listItems.add(buildMenuListItem(R.string.reading_list_mark_as_read, 0, 0));
            }
            listItems.add(buildMenuListItem(R.string.bookmark_item_select, 0, 0));
            listItems.add(buildMenuListItem(R.string.bookmark_item_delete, 0, 0));
            if (!ChromeApplicationImpl.isVivaldi())
            if (ReadingListFeatures.shouldAllowBookmarkTypeSwapping()) {
                listItems.add(buildMenuListItem(R.string.bookmark_item_edit, 0, 0));
                listItems.add(buildMenuListItem(R.string.bookmark_item_move, 0, 0));
            }
        } else {
            listItems.add(buildMenuListItem(R.string.bookmark_item_select, 0, 0));
            listItems.add(buildMenuListItem(R.string.bookmark_item_edit, 0, 0));
            listItems.add(buildMenuListItem(R.string.bookmark_item_move, 0, 0, canMove));
            listItems.add(buildMenuListItem(R.string.bookmark_item_delete, 0, 0));
        }

        if (mDelegate.getCurrentState() == BookmarkUIState.STATE_SEARCHING) {
            listItems.add(buildMenuListItem(R.string.bookmark_show_in_folder, 0, 0));
        } else if (mDelegate.getCurrentState() == BookmarkUIState.STATE_FOLDER
                && mLocation != Location.SOLO && canReorder) {
            // Only add move up / move down buttons if there is more than 1 item
            if (mLocation != Location.TOP) {
                listItems.add(buildMenuListItem(R.string.menu_item_move_up, 0, 0));
            }
            if (mLocation != Location.BOTTOM) {
                listItems.add(buildMenuListItem(R.string.menu_item_move_down, 0, 0));
            }
        }

        return listItems;
    }

    private ListMenu getListMenu() {
        ModelList listItems = getItems();
        ListMenu.Delegate delegate = item -> {
            int textId = item.get(ListMenuItemProperties.TITLE_ID);
            if (textId == R.string.bookmark_item_select) {
                setChecked(mDelegate.getSelectionDelegate().toggleSelectionForItem(mBookmarkId));
                RecordUserAction.record("Android.BookmarkPage.SelectFromMenu");
                if (mBookmarkId.getType() == BookmarkType.READING_LIST) {
                    RecordUserAction.record("Android.BookmarkPage.ReadingList.SelectFromMenu");
                }
            } else if (textId == R.string.bookmark_item_edit) {
                BookmarkItem bookmarkItem = mDelegate.getModel().getBookmarkById(mBookmarkId);
                if (bookmarkItem.isFolder()) {
                    BookmarkAddEditFolderActivity.startEditFolderActivity(
                            getContext(), bookmarkItem.getId());
                } else {
                    BookmarkUtils.startEditActivity(getContext(), bookmarkItem.getId());
                }
            } else if (textId == R.string.reading_list_mark_as_read) {
                BookmarkItem bookmarkItem = mDelegate.getModel().getBookmarkById(mBookmarkId);
                mDelegate.getModel().setReadStatusForReadingList(
                        bookmarkItem.getUrl(), true /*read*/);
                RecordUserAction.record("Android.BookmarkPage.ReadingList.MarkAsRead");
            } else if (textId == R.string.bookmark_item_move) {
                BookmarkFolderSelectActivity.startFolderSelectActivity(getContext(), mBookmarkId);
                RecordUserAction.record("MobileBookmarkManagerMoveToFolder");
            } else if (textId == R.string.bookmark_item_delete) {
                if (mDelegate != null && mDelegate.getModel() != null) {
                    mDelegate.getModel().deleteBookmarks(mBookmarkId);
                    RecordUserAction.record("Android.BookmarkPage.RemoveItem");
                    if (mBookmarkId.getType() == BookmarkType.READING_LIST) {
                        RecordUserAction.record("Android.BookmarkPage.ReadingList.RemoveItem");
                    }
                }
            } else if (textId == R.string.bookmark_show_in_folder) {
                BookmarkItem bookmarkItem = mDelegate.getModel().getBookmarkById(mBookmarkId);
                mDelegate.openFolder(bookmarkItem.getParentId());
                mDelegate.highlightBookmark(mBookmarkId);
                RecordUserAction.record("MobileBookmarkManagerShowInFolder");
            } else if (textId == R.string.menu_item_move_up) {
                mDelegate.moveUpOne(mBookmarkId);
                RecordUserAction.record("MobileBookmarkManagerMoveUp");
            } else if (textId == R.string.menu_item_move_down) {
                mDelegate.moveDownOne(mBookmarkId);
                RecordUserAction.record("MobileBookmarkManagerMoveDown");
            };
        };
        return new BasicListMenu(getContext(), listItems, delegate);
    }

    // FrameLayout implementation.
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        LayoutInflater.from(getContext()).inflate(R.layout.list_menu_button, mContentView);
        mMoreIcon = findViewById(R.id.more);
        mMoreIcon.setDelegate(getListMenuButtonDelegate());

        mDragHandle = mEndButtonView;
        mDragHandle.setImageResource(R.drawable.ic_drag_handle_grey600_24dp);
        ApiCompatibilityUtils.setImageTintList(mDragHandle,
                AppCompatResources.getColorStateList(
                        getContext(), R.color.default_icon_color_tint_list));
    }

    private ListMenuButtonDelegate getListMenuButtonDelegate() {
        return this::getListMenu;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mIsAttachedToWindow = true;
        if (mDelegate != null) {
            initialize();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mIsAttachedToWindow = false;
        cleanup();
    }

    // SelectableItem implementation.
    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {
        super.onSelectionStateChange(selectedBookmarks);
        updateVisualState();
    }

    // BookmarkUIObserver implementation.
    @Override
    public void onDestroy() {
        cleanup();
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {}

    @Override
    public void onSearchStateSet() {}

    @VisibleForTesting
    public boolean isItemSelected() {
        return mDelegate.getSelectionDelegate().isItemSelected(mBookmarkId);
    }

    void setDragHandleOnTouchListener(OnTouchListener l) {
        mDragHandle.setOnTouchListener(l);
    }

    public String getTitle() {
        return String.valueOf(mTitleView.getText());
    }

    private boolean isDragActive() {
        return mDelegate.getDragStateDelegate().getDragActive();
    }

    @Override
    public boolean onLongClick(View view) {
        // Override is needed in order to support long-press-to-drag on already-selected items.
        if (isDragActive() && isItemSelected()) return true;
        RecordUserAction.record("MobileBookmarkManagerLongPressToggleSelect");
        return super.onLongClick(view);
    }

    @Override
    public void onClick(View view) {
        // Override is needed in order to allow items to be selected / deselected with a click.
        // Since we override #onLongClick(), we cannot rely on the base class for this behavior.
        if (isDragActive()) {
            toggleSelectionForItem(getItem());
            RecordUserAction.record("MobileBookmarkManagerTapToggleSelect");
        } else {
            super.onClick(view);
        }
    }

    @VisibleForTesting
    public View getDragHandleViewForTests() {
        return mDragHandle;
    }


}
