// Copyright 2022 Vivaldi Technologies. All rights reserved.

#ifndef IOS_CHROME_BROWSER_UI_VIVALDI_SPEED_DIAL_URL_ITEM_CELL_H_
#define IOS_CHROME_BROWSER_UI_VIVALDI_SPEED_DIAL_URL_ITEM_CELL_H_

#import <UIKit/UIKit.h>

#import "ios/chrome/browser/ui/ntp/vivaldi_speed_dial_item.h"
#import "ios/chrome/common/ui/favicon/favicon_attributes.h"

// The cell that renders the speed dial URL items
@interface VivaldiSpeedDialURLItemCell : UICollectionViewCell

// INITIALIZER
- (instancetype)initWithFrame:(CGRect)rect;

// SETTERS
- (void)configureCellWith:(const VivaldiSpeedDialItem*)item;
- (void)configureCellWithAttributes:(const FaviconAttributes*)attributes;

@end

#endif  // IOS_CHROME_BROWSER_UI_VIVALDI_SPEED_DIAL_URL_ITEM_CELL_H_
