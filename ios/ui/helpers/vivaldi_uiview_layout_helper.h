// Copyright 2022 Vivaldi Technologies. All rights reserved.

#ifndef IOS_CHROME_BROWSER_UI_VIVALDI_LAYOUT_HELPER_H_
#define IOS_CHROME_BROWSER_UI_VIVALDI_LAYOUT_HELPER_H_

#import <UIKit/UIKit.h>

@interface UIView(VivaldiLayout)

#pragma mark:- SETTERS
/// Applies constraint to the view with passed anchors alongside padding and size
- (void) anchorTop:(NSLayoutYAxisAnchor*)top
          leading:(NSLayoutXAxisAnchor*)leading
           bottom:(NSLayoutYAxisAnchor*)bottom
         trailing:(NSLayoutXAxisAnchor*)trailing
          padding:(UIEdgeInsets)padding
             size:(CGSize)size;

/// Applies constraint to the view with passed anchors.
/// The default padding is zero and size is not affected.
- (void) anchorTop:(NSLayoutYAxisAnchor*)top
          leading:(NSLayoutXAxisAnchor*)leading
           bottom:(NSLayoutYAxisAnchor*)bottom
         trailing:(NSLayoutXAxisAnchor*)trailing;

/// Applies constraint to the view with passed anchors with padding.
/// Size is not affected.
- (void) anchorTop:(NSLayoutYAxisAnchor*)top
          leading:(NSLayoutXAxisAnchor*)leading
           bottom:(NSLayoutYAxisAnchor*)bottom
         trailing:(NSLayoutXAxisAnchor*)trailing
          padding:(UIEdgeInsets)padding;

/// Applies constraint to the view with passed anchors with size.
/// The default padding is zero.
- (void) anchorTop:(NSLayoutYAxisAnchor*)top
          leading:(NSLayoutXAxisAnchor*)leading
           bottom:(NSLayoutYAxisAnchor*)bottom
         trailing:(NSLayoutXAxisAnchor*)trailing
             size:(CGSize)size;

/// The child view takes the size of the parent view with user provided padding.
- (void) fillSuperviewWithPadding:(UIEdgeInsets)padding;
/// The child view takes the size of the parent view without padding. The default padding is zero.
- (void) fillSuperview;

/// The child view placed in the center of the superview with user provided size.
- (void) centerInSuperviewWithSize:(CGSize)size;
/// The child view placed in the center of the superview. The size is calculated from the constaints of the
/// child view of this view.
- (void) centerInSuperview;

/// Center the view horizontally within the superview
- (void) centerXInSuperview;
/// Center the view vertically within the superview
- (void) centerYInSuperview;

/// Match the constraints of a given view
- (void) matchToView:(UIView*)view;

/// Set width for a view
- (void) setWidthWithConstant:(CGFloat)constant;
/// Set height for a view
- (void) setHeightWithConstant:(CGFloat)constant;
/// Set size for a view
- (void) setViewSize:(CGSize)size;


#pragma mark:- GETTERS
/// Returns safe top anchor
- (NSLayoutYAxisAnchor*)safeTopAnchor;
/// Returns safe leading/left anchor
- (NSLayoutXAxisAnchor*)safeLeftAnchor;
/// Returns safe trailing/right anchor
- (NSLayoutXAxisAnchor*)safeRightAnchor;
/// Returns safe bottom anchor
- (NSLayoutYAxisAnchor*)safeBottomAnchor;

@end

#endif  // IOS_CHROME_BROWSER_UI_VIVALDI_LAYOUT_HELPER_H_
