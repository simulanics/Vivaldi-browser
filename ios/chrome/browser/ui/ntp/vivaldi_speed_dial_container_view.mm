// Copyright 2022 Vivaldi Technologies. All rights reserved.

#import "ios/chrome/browser/ui/ntp/vivaldi_speed_dial_container_view.h"

#import "UIKit/UIKit.h"

#import "ios/chrome/browser/favicon/favicon_loader.h"
#import "ios/chrome/browser/ui/ntp/cells/vivaldi_speed_dial_folder_item_cell.h"
#import "ios/chrome/browser/ui/ntp/cells/vivaldi_speed_dial_url_item_cell.h"
#import "ios/chrome/browser/ui/ntp/vivaldi_ntp_constants.h"
#import "ios/chrome/common/ui/favicon/favicon_attributes.h"
#import "ios/chrome/common/ui/favicon/favicon_constants.h"
#import "ios/ui/helpers/vivaldi_uiview_layout_helper.h"
#import "vivaldi/ios/grit/vivaldi_ios_native_strings.h"
#include "ios/chrome/grit/ios_strings.h"
#include "ui/base/device_form_factor.h"
#include "ui/base/l10n/l10n_util_mac.h"
#include "url/gurl.h"

using ui::GetDeviceFormFactor;
using ui::DEVICE_FORM_FACTOR_TABLET;

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

// NAMESPACE
namespace {
// Cell Identifier for the cell types
NSString* cellIdFolderType = @"cellIdFolder";
NSString* cellIdURLType = @"cellIdURL";

}

@interface VivaldiSpeedDialContainerView() <UICollectionViewDataSource,
                                            UICollectionViewDelegate,
                                            UICollectionViewDragDelegate,
                                            UICollectionViewDropDelegate>
// FaviconLoader is a keyed service that uses LargeIconService to retrieve
// favicon images.
@property(nonatomic,assign) FaviconLoader* faviconLoader;
// Collection view that holds the speed dial folder's children
@property(weak,nonatomic) UICollectionView *collectionView;
// Parent speed dial folder
@property(assign,nonatomic) VivaldiSpeedDialItem* parent;
// Array to store the children to populate on the collection view
@property(strong,nonatomic) NSMutableArray *speedDialItems;
// A BOOL to keep track the device orientation
@property(assign,nonatomic) BOOL isDeviceLandscape;
@end

@implementation VivaldiSpeedDialContainerView

@synthesize faviconLoader = _faviconLoader;
@synthesize collectionView = _collectionView;
@synthesize parent = _parent;
@synthesize speedDialItems = _speedDialItems;
@synthesize isDeviceLandscape = _isDeviceLandscape;

#pragma mark - INITIALIZER
- (instancetype)init {
  if (self = [super initWithFrame:CGRectZero]) {
    self.backgroundColor = UIColor.clearColor;
    [self setUpUI];
  }
  return self;
}

#pragma mark - SET UP UI COMPONENTS
- (void)setUpUI {

  UICollectionViewLayout *layout= [self createLayout];
  UICollectionView* collectionView =
    [[UICollectionView alloc] initWithFrame:CGRectZero
                       collectionViewLayout:layout];
  _collectionView = collectionView;
  [_collectionView setDataSource: self];
  [_collectionView setDelegate: self];
  [_collectionView setDropDelegate: self];
  [_collectionView setDragDelegate: self];
  _collectionView.dragInteractionEnabled = YES;
  _collectionView.showsHorizontalScrollIndicator = NO;
  _collectionView.showsVerticalScrollIndicator = NO;
  _collectionView.contentInsetAdjustmentBehavior =
    UIScrollViewContentInsetAdjustmentNever;

  [_collectionView registerClass:[VivaldiSpeedDialURLItemCell class]
      forCellWithReuseIdentifier:cellIdURLType];
  [_collectionView registerClass:[VivaldiSpeedDialFolderItemCell class]
      forCellWithReuseIdentifier:cellIdFolderType];
  [_collectionView setBackgroundColor:[UIColor clearColor]];

  [self addSubview:_collectionView];
  [_collectionView fillSuperview];
}

#pragma mark - SETTERS
- (void)configureWith:(NSArray*)speedDials
               parent:(VivaldiSpeedDialItem*)parent
        faviconLoader:(FaviconLoader*)faviconLoader
    deviceOrientation:(BOOL)isLandscape {
  self.parent = parent;
  self.faviconLoader = faviconLoader;
  self.isDeviceLandscape = isLandscape;
  self.speedDialItems = [[NSMutableArray alloc] initWithArray:speedDials];
  [self.collectionView reloadData];
}

- (void)setDeviceOrientation:(BOOL)isLandscape {
  self.isDeviceLandscape = isLandscape;
  [self.collectionView.collectionViewLayout invalidateLayout];
}

#pragma mark - COLLECTIONVIEW DATA SOURCE

- (NSInteger)collectionView:(UICollectionView *)collectionView
     numberOfItemsInSection:(NSInteger)section {
  // '+1' is for the 'New Speed Dial' item on the start page that's reponsible
  // for opening bookmark/speed dial editor.
  return self.speedDialItems.count + 1;
}

- (UICollectionViewCell*)collectionView:(UICollectionView*)collectionView
                 cellForItemAtIndexPath:(NSIndexPath*)indexPath {

  // If there's no more object then load the new speed dial creation tile
  if (indexPath.row == (long)self.speedDialItems.count) {
    VivaldiSpeedDialFolderItemCell *cell =
    [collectionView dequeueReusableCellWithReuseIdentifier:cellIdFolderType
                                              forIndexPath:indexPath];
    [cell configureCellWith:nil addNew:YES];
    return cell;
  }

  VivaldiSpeedDialItem* item =
    [self.speedDialItems objectAtIndex: indexPath.row];

  if (item.isFolder) {
    VivaldiSpeedDialFolderItemCell *cell =
      [collectionView dequeueReusableCellWithReuseIdentifier:cellIdFolderType
                                                forIndexPath:indexPath];
    [cell configureCellWith:item addNew:NO];
    return cell;
  } else {
    VivaldiSpeedDialURLItemCell *cell =
      [collectionView dequeueReusableCellWithReuseIdentifier:cellIdURLType
                                                forIndexPath:indexPath];
    [cell configureCellWith:item];
    [self loadFaviconForItem:item forCell:cell fallbackToGoogleServer:YES];
    return cell;
  }
}

// Asynchronously loads favicon for given index path. The loads are cancelled
// upon cell reuse automatically.  When the favicon is not found in cache, try
// loading it from a Google server if `fallbackToGoogleServer` is YES,
// otherwise, use the fall back icon style.
- (void)loadFaviconForItem:(VivaldiSpeedDialItem*)item
                   forCell:(VivaldiSpeedDialURLItemCell*)cell
    fallbackToGoogleServer:(BOOL)fallbackToGoogleServer {

  // Start loading a favicon.
  __weak VivaldiSpeedDialContainerView* weakSelf = self;
  GURL blockURL(item.url);
  auto faviconLoadedBlock = ^(FaviconAttributes* attributes) {
    VivaldiSpeedDialContainerView* strongSelf = weakSelf;
    if (!strongSelf)
      return;
    [cell configureCellWithAttributes:attributes];
  };

  CGFloat desiredFaviconSizeInPoints = kDesiredSmallFaviconSizePt;
  CGFloat minFaviconSizeInPoints = kMinFaviconSizePt;

  self.faviconLoader->FaviconForPageUrl(
      blockURL, desiredFaviconSizeInPoints, minFaviconSizeInPoints,
      /*fallback_to_google_server=*/fallbackToGoogleServer, faviconLoadedBlock);
}

#pragma mark - COLLECTIONVIEW DELEGATE
- (void)collectionView:(UICollectionView *)collectionView
didSelectItemAtIndexPath:(NSIndexPath *)indexPath {
  // If last item tapped then open the view controller for adding new speed dial
  // item. Otherwise, pass the node which will be handleed by the delegate
  // method implementation.
  if (indexPath.row == (long)self.speedDialItems.count) {
    // Add speed dial item. The boolean in the method represents whether its
    // speed dial item or speed dial folder
    if (self.delegate)
        [self.delegate didSelectAddNewSpeedDial:NO
                                         parent:self.parent];
  } else {
    VivaldiSpeedDialItem* item =
      [self.speedDialItems objectAtIndex: indexPath.row];
    if (self.delegate)
        [self.delegate didSelectItem:item
                              parent:self.parent];
  }
}

#pragma mark - COLLECTIONVIEW DRAG DELEGATE

- (NSArray<UIDragItem *> *)collectionView:(UICollectionView *)collectionView
             itemsForBeginningDragSession:(id<UIDragSession>)session
                              atIndexPath:(NSIndexPath *)indexPath {
  // If there's at least two items apart from the add speed dial tile then
  // enable the drag and drop otherwise return
  if (!(self.speedDialItems.count > 1))
    return @[];

  // Disable drag and drop for the add speed dial tile
  if (indexPath.row == (long)self.speedDialItems.count)
    return @[];

  VivaldiSpeedDialItem* item =
    [self.speedDialItems objectAtIndex: indexPath.row];
  NSItemProvider* itemProvider = [[NSItemProvider alloc] initWithObject:item];
  UIDragItem* dragItem = [[UIDragItem alloc] initWithItemProvider:itemProvider];
  dragItem.localObject = item;
  return @[dragItem];
}

#pragma mark - COLLECTIONVIEW DROP DELEGATE

- (UICollectionViewDropProposal*)
  collectionView:(UICollectionView*)collectionView
dropSessionDidUpdate:(id<UIDropSession>)session
withDestinationIndexPath:(NSIndexPath *)destinationIndexPath {

  if (collectionView.hasActiveDrag) {
    UICollectionViewDropProposal *proposal =
      [[UICollectionViewDropProposal alloc]
        initWithDropOperation:UIDropOperationMove
          intent:UICollectionViewDropIntentInsertAtDestinationIndexPath];
    return proposal;
  }

  UICollectionViewDropProposal *proposal =
    [[UICollectionViewDropProposal alloc]
     initWithDropOperation:UIDropOperationForbidden];
  return proposal;
}

- (void)collectionView:(UICollectionView *)collectionView
performDropWithCoordinator:(id<UICollectionViewDropCoordinator>)coordinator {
  NSIndexPath* destinationIndexPath;

  NSIndexPath* indexPath = coordinator.destinationIndexPath;
  if (indexPath) {
    destinationIndexPath = indexPath;
  } else {
    NSInteger row = [collectionView numberOfItemsInSection:0];
    destinationIndexPath = [NSIndexPath indexPathForRow:row inSection:0];
  }

  if (coordinator.proposal.operation == UIDropOperationMove) {
    [self reorderItems:coordinator
  destinationIndexPath:destinationIndexPath
        collectionView:collectionView];
  }
}

- (void)reorderItems:(id<UICollectionViewDropCoordinator>)coordinator
destinationIndexPath:(NSIndexPath*)destinationIndexPath
      collectionView:(UICollectionView*)collectionView {

  id<UICollectionViewDropItem> item = coordinator.items.firstObject;
  if (!item)
    return;
  NSIndexPath* sourceIndexPath = item.sourceIndexPath;
  if (!sourceIndexPath)
    return;
  VivaldiSpeedDialItem* dragItem = item.dragItem.localObject;
  [collectionView performBatchUpdates:^{
    [self.speedDialItems removeObjectAtIndex: sourceIndexPath.item];
    [self.speedDialItems insertObject:dragItem
                              atIndex:destinationIndexPath.item];
    [collectionView deleteItemsAtIndexPaths:@[sourceIndexPath]];
    [collectionView insertItemsAtIndexPaths:@[destinationIndexPath]];
  } completion:nil];

  [coordinator dropItem:item.dragItem toItemAtIndexPath:destinationIndexPath];

  NSInteger distance = destinationIndexPath.item - sourceIndexPath.item;
  NSInteger newPosition = distance > 0 ?
    (destinationIndexPath.item + 1) : destinationIndexPath.item;
  if (self.delegate)
    [self.delegate didMoveItemByDragging:dragItem
                                  parent:self.parent
                              toPosition:newPosition];
}

#pragma mark - CONTEXT MENU
- (UIContextMenuConfiguration*)collectionView:(UICollectionView *)collectionView
    contextMenuConfigurationForItemAtIndexPath:(NSIndexPath *)indexPath
                                         point:(CGPoint)point {
  // If the last item is tapped show the context menu for add new speed dial
  // item
  if (indexPath.row == (long)self.speedDialItems.count) {
    return [self contextMenuForAddNewItem];
  } else {
    return [self contextMenuForRegularItem: indexPath];
  }
}

/// Returns the context menu item for regular item such as a an speed dial
/// URL item or speed dial folder item.
- (UIContextMenuConfiguration*)contextMenuForRegularItem:
(NSIndexPath*)indexPath {
  UIContextMenuConfiguration* config =
  [UIContextMenuConfiguration configurationWithIdentifier:nil
                                          previewProvider:nil
                                           actionProvider:^UIMenu*
   _Nullable(NSArray<UIMenuElement*>* _Nonnull menuActions) {

    NSString* editActionTitle = l10n_util::GetNSString(IDS_IOS_EDIT_SPEED_DIAL);
    UIAction * editAction =
      [UIAction actionWithTitle:editActionTitle
                          image:[UIImage systemImageNamed:@"pencil"]
                     identifier:nil
                        handler:^(__kindof UIAction* _Nonnull action) {
        // Edit button action
        if (self.delegate) {
          VivaldiSpeedDialItem* item =
            [self.speedDialItems objectAtIndex: indexPath.row];
          [self.delegate didSelectEditItem:item
                                    parent:self.parent];
        }
      }];

    NSString* moveActionTitle = l10n_util::GetNSString(IDS_IOS_MOVE_ITEM);
    UIAction * moveAction =
      [UIAction actionWithTitle:moveActionTitle
                          image:[UIImage systemImageNamed:@"folder.badge.minus"]
                     identifier:nil
                        handler:^(__kindof UIAction* _Nonnull action) {
        // Move out of folder action
        if (self.delegate) {
          VivaldiSpeedDialItem* item =
            [self.speedDialItems objectAtIndex: indexPath.row];
          [self.delegate didSelectMoveItem:item
                                    parent:self.parent];
        }
      }];

    NSString* deleteActionTitle =
      l10n_util::GetNSString(IDS_IOS_SPEED_DIAL_ITEM_DELETE);
    UIAction * deleteAction =
      [UIAction actionWithTitle:deleteActionTitle
                          image:[UIImage systemImageNamed:@"trash"]
                     identifier:nil
                        handler:^(__kindof UIAction* _Nonnull action) {
        // Delete button action
        if (self.delegate) {
          VivaldiSpeedDialItem* item =
            [self.speedDialItems objectAtIndex: indexPath.row];
          [self.delegate didSelectDeleteItem:item
                                      parent:self.parent];
        }
      }];
    deleteAction.attributes = UIMenuElementAttributesDestructive;

    UIMenu* menu = [UIMenu menuWithTitle:@"" children:@[
      editAction, moveAction, deleteAction
    ]];
    return menu;

  }];

  return config;
}

/// Returns the context menu item for new speed dial item
- (UIContextMenuConfiguration*)contextMenuForAddNewItem {
  UIContextMenuConfiguration* config =
  [UIContextMenuConfiguration configurationWithIdentifier:nil
                                          previewProvider:nil
                                           actionProvider:^UIMenu*
   _Nullable(NSArray<UIMenuElement*>* _Nonnull menuActions) {
    NSString* newSpeedDialActionTitle =
      l10n_util::GetNSString(IDS_IOS_NEW_SPEED_DIAL);
    UIAction * newSpeedDialAction =
      [UIAction actionWithTitle:newSpeedDialActionTitle
                          image:[UIImage systemImageNamed:@"plus"]
                     identifier:nil
                        handler:^(__kindof UIAction* _Nonnull action) {
        // Add new speed dial action
        if (self.delegate)
          [self.delegate didSelectAddNewSpeedDial:NO
                                           parent:self.parent];
      }];

    NSString* newFolderActionTitle =
      l10n_util::GetNSString(IDS_IOS_NEW_SPEED_DIAL_FOLDER);
    UIAction * newFolderAction =
      [UIAction actionWithTitle:newFolderActionTitle
                          image:[UIImage systemImageNamed:@"folder.badge.plus"]
                     identifier:nil
                        handler:^(__kindof UIAction* _Nonnull action) {
        // Add new speed dial folder action
        if (self.delegate)
          [self.delegate didSelectAddNewSpeedDial:YES
                                           parent:self.parent];
    }];

    UIMenu* menu = [UIMenu menuWithTitle:@"" children:@[
      newSpeedDialAction, newFolderAction
    ]];
    return menu;

  }];

  return config;
}

#pragma mark - SET UP UI COMPONENTS

/// Create and return the comositional layout for the collection view
- (UICollectionViewCompositionalLayout*)createLayout {
  UICollectionViewCompositionalLayout *layout =
    [[UICollectionViewCompositionalLayout alloc]
      initWithSectionProvider:
       ^NSCollectionLayoutSection*(NSInteger sectionIndex,
       id<NSCollectionLayoutEnvironment> layoutEnvironment) {
    return [self layoutSectionFor:sectionIndex environment:layoutEnvironment];
  }];

  return layout;
}

- (NSCollectionLayoutSection*)layoutSectionFor:(NSInteger)index
     environment:(id<NSCollectionLayoutEnvironment>)environment {

  CGFloat gridItemSize = [self getItemSize];
  CGFloat sectionPadding =
    self.isCurrentDeviceTablet ? self.getSectionPaddingForTablet
                               : self.getSectionPaddingForPhone;
  CGFloat itemPadding = [self getItemPadding];

  NSCollectionLayoutSize *itemSize =
    [NSCollectionLayoutSize
      sizeWithWidthDimension:[NSCollectionLayoutDimension
                                fractionalWidthDimension:gridItemSize]
             heightDimension:[NSCollectionLayoutDimension
                                fractionalWidthDimension:gridItemSize]];

  NSCollectionLayoutItem *item =
    [NSCollectionLayoutItem itemWithLayoutSize:itemSize];
  NSArray *items = [[NSArray alloc] initWithObjects:item, nil];

  item.contentInsets = NSDirectionalEdgeInsetsMake(itemPadding,
                                                   itemPadding,
                                                   itemPadding,
                                                   itemPadding);

  NSCollectionLayoutSize *groupSize =
    [NSCollectionLayoutSize
      sizeWithWidthDimension:[NSCollectionLayoutDimension
                                fractionalWidthDimension:1.0]
             heightDimension:[NSCollectionLayoutDimension
                                fractionalWidthDimension:gridItemSize]];
  NSCollectionLayoutGroup *group =
    [NSCollectionLayoutGroup horizontalGroupWithLayoutSize:groupSize
                                                  subitems:items];

  NSCollectionLayoutSection *section =
    [NSCollectionLayoutSection sectionWithGroup:group];
  section.contentInsets = NSDirectionalEdgeInsetsMake(vSDContainerTopPadding,
                                                      sectionPadding,
                                                      vSDContainerTopPadding,
                                                      sectionPadding);
  return section;
}

/// Returns whether current device is iPhone or iPad.
- (BOOL)isCurrentDeviceTablet {
  return GetDeviceFormFactor() == DEVICE_FORM_FACTOR_TABLET;
}

/// Returns the size for each speed dial item tile.
- (CGFloat)getItemSize {
  if (self.isCurrentDeviceTablet) {
    return vSDItemSizeMultiplieriPad;
  } else {
    if (self.isDeviceLandscape) {
      return vSDItemSizeMultiplieriPhoneLandscape;
    } else {
      return vSDItemSizeMultiplieriPhonePortrait;
    }
  }
}

/// Return the item padding for iPhone and iPad
/// Same item padding is used for both portrait and landscape mode.
- (CGFloat)getItemPadding {
  return self.isCurrentDeviceTablet ? vSDItemPaddingiPad : vSDItemPaddingiPhone;
}

/// Returns the section padding for tablet
- (CGFloat)getSectionPaddingForTablet {
  return self.isDeviceLandscape ?
    vSDSectionPaddingiPadLandscape : vSDSectionPaddingiPortrait;
}

/// Returns the section padding for iPhone
- (CGFloat)getSectionPaddingForPhone {
  return self.isDeviceLandscape ?
    vSDSectionPaddingiPhoneLandscape : vSDSectionPaddingiPhonePortrait;
}

@end
