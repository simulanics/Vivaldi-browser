// Copyright 2022 Vivaldi Technologies. All rights reserved.

#ifndef IOS_CHROME_BROWSER_UI_VIVALDI_SPEED_DIAL_CONTAINER_CELL_H_
#define IOS_CHROME_BROWSER_UI_VIVALDI_SPEED_DIAL_CONTAINER_CELL_H_

#import <UIKit/UIKit.h>

#import "ios/chrome/browser/favicon/favicon_loader.h"
#import "ios/chrome/browser/ui/ntp/vivaldi_speed_dial_container_delegate.h"
#import "ios/chrome/browser/ui/ntp/vivaldi_speed_dial_container_view.h"

// The cell to contain the children of speed dial folder
@interface VivaldiSpeedDialContainerCell : UICollectionViewCell

// INITIALIZER
- (instancetype)initWithFrame:(CGRect)rect;

// DELEGATE
@property (nonatomic, weak) id<VivaldiSpeedDialContainerDelegate> delegate;

// SETTERS
- (void)configureWith:(NSArray*)speedDials
               parent:(VivaldiSpeedDialItem*)parent
        faviconLoader:(FaviconLoader*)faviconLoader
    deviceOrientation:(bool)isLandscape;
- (void)setDeviceOrientation:(Boolean)isLandscape;
- (void)setCurrentPage:(NSInteger)page;
@end

#endif  // IOS_CHROME_BROWSER_UI_VIVALDI_SPEED_DIAL_CONTAINER_CELL_H_
