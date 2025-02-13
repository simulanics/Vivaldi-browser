// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/content_suggestions/cells/content_suggestions_tile_view.h"

#import "ios/chrome/browser/ui/content_suggestions/cells/content_suggestions_tile_layout_util.h"
#import "ios/chrome/browser/ui/content_suggestions/content_suggestions_feature.h"
#import "ios/chrome/browser/ui/util/dynamic_type_util.h"
#import "ios/chrome/common/ui/colors/semantic_color_names.h"
#import "ios/chrome/common/ui/util/constraints_ui_util.h"
#import "ios/chrome/common/ui/util/dynamic_type_util.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {

const NSInteger kLabelNumLines = 2;
const CGFloat kSpaceIconTitle = 10;
const CGFloat kIconSize = 56;

// Standard width of tiles.
const CGFloat kPreferredMaxWidth = 74;
const CGFloat kPreferredMaxWidthWide = 83;

// Non-standard width of tiles. (Used only when the Content Suggestions UI
// Module Refresh feature is enabled.)
const CGFloat kModulePreferredMaxWidth = 74;
const CGFloat kModulePreferredMaxWidthWide = 83;

}  // namespace

@interface ContentSuggestionsTileView ()
// Hold onto the created interaction for pointer support so it can be removed
// when the view goes away.
@property(nonatomic, strong)
    UIPointerInteraction* pointerInteraction API_AVAILABLE(ios(13.4));
@end

@implementation ContentSuggestionsTileView

- (instancetype)initWithFrame:(CGRect)frame placeholder:(BOOL)isPlaceholder {
  self = [super initWithFrame:frame];
  if (self) {
    _titleLabel = [[UILabel alloc] init];
    _titleLabel.textColor = [UIColor colorNamed:kTextSecondaryColor];
    _titleLabel.font = [self titleLabelFont];
    _titleLabel.textAlignment = NSTextAlignmentCenter;

    // Since modules are given more horizontal space on iPad, allow for more
    // estimated width for the label to calculate content size.
    CGFloat preferredWidth = self.traitCollection.horizontalSizeClass ==
                                     UIUserInterfaceSizeClassRegular
                                 ? kPreferredMaxWidthWide
                                 : kPreferredMaxWidth;
    CGFloat modulePreferredWidth = self.traitCollection.horizontalSizeClass ==
                                           UIUserInterfaceSizeClassRegular
                                       ? kModulePreferredMaxWidthWide
                                       : kModulePreferredMaxWidth;

    _titleLabel.preferredMaxLayoutWidth =
        IsContentSuggestionsUIModuleRefreshEnabled() ? modulePreferredWidth
                                                     : preferredWidth;
    _titleLabel.numberOfLines = kLabelNumLines;
    _imageContainerView = [[UIView alloc] init];
    _titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
    _imageContainerView.translatesAutoresizingMaskIntoConstraints = NO;

    [self addSubview:_titleLabel];

    // The squircle background view.
    UIImageView* backgroundView =
        [[UIImageView alloc] initWithFrame:self.bounds];
    backgroundView.translatesAutoresizingMaskIntoConstraints = NO;
    UIImage* backgroundImage = [[UIImage imageNamed:@"ntp_most_visited_tile"]
        imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
    backgroundView.image = backgroundImage;
    backgroundView.tintColor = [UIColor colorNamed:kGrey100Color];
    [self addSubview:backgroundView];
    [self addSubview:_imageContainerView];

    [NSLayoutConstraint activateConstraints:@[
      [backgroundView.widthAnchor constraintEqualToConstant:kIconSize],
      [backgroundView.heightAnchor
          constraintEqualToAnchor:backgroundView.widthAnchor],
      [backgroundView.centerXAnchor
          constraintEqualToAnchor:_titleLabel.centerXAnchor],
    ]];
    AddSameCenterConstraints(_imageContainerView, backgroundView);
    UIView* containerView = backgroundView;

    if (IsContentSuggestionsUIModuleRefreshEnabled() && isPlaceholder) {
      ApplyVisualConstraintsWithMetrics(
          @[ @"V:|[container]-(space)-[title]-(>=0)-|" ],
          @{@"container" : containerView, @"title" : _titleLabel},
          @{@"space" : @(kSpaceIconTitle)});
      [NSLayoutConstraint activateConstraints:@[
        [_titleLabel.widthAnchor constraintEqualToConstant:kIconSize],
        [_titleLabel.centerXAnchor constraintEqualToAnchor:self.centerXAnchor],
      ]];
    } else {
      ApplyVisualConstraintsWithMetrics(
          @[ @"V:|[container]-(space)-[title]|", @"H:|[title]|" ],
          @{@"container" : containerView, @"title" : _titleLabel},
          @{@"space" : @(kSpaceIconTitle)});
    }

    _imageBackgroundView = backgroundView;

    _pointerInteraction = [[UIPointerInteraction alloc] initWithDelegate:self];
    [self addInteraction:self.pointerInteraction];
  }
  return self;
}

- (void)dealloc {
  [self removeInteraction:self.pointerInteraction];
  self.pointerInteraction = nil;
}

// Returns the font size for the location label.
- (UIFont*)titleLabelFont {
  if (IsContentSuggestionsUIModuleRefreshEnabled()) {
    return [UIFont preferredFontForTextStyle:UIFontTextStyleCaption1];
  } else {
    return PreferredFontForTextStyleWithMaxCategory(
        UIFontTextStyleCaption1,
        self.traitCollection.preferredContentSizeCategory,
        UIContentSizeCategoryAccessibilityLarge);
  }
}

#pragma mark - UIView

- (void)traitCollectionDidChange:(UITraitCollection*)previousTraitCollection {
  [super traitCollectionDidChange:previousTraitCollection];
  if (previousTraitCollection.preferredContentSizeCategory !=
      self.traitCollection.preferredContentSizeCategory) {
    self.titleLabel.font = [self titleLabelFont];
  }
}

#pragma mark - UIPointerInteractionDelegate

- (UIPointerRegion*)pointerInteraction:(UIPointerInteraction*)interaction
                      regionForRequest:(UIPointerRegionRequest*)request
                         defaultRegion:(UIPointerRegion*)defaultRegion
    API_AVAILABLE(ios(13.4)) {
  return defaultRegion;
}

- (UIPointerStyle*)pointerInteraction:(UIPointerInteraction*)interaction
                       styleForRegion:(UIPointerRegion*)region
    API_AVAILABLE(ios(13.4)) {
  // The preview APIs require the view to be in a window. Ensure they are before
  // proceeding.
  if (!self.window)
    return nil;

  UITargetedPreview* preview =
      [[UITargetedPreview alloc] initWithView:_imageContainerView];
  UIPointerHighlightEffect* effect =
      [UIPointerHighlightEffect effectWithPreview:preview];
  UIPointerShape* shape =
      [UIPointerShape shapeWithRoundedRect:_imageContainerView.frame
                              cornerRadius:8.0];
  return [UIPointerStyle styleWithEffect:effect shape:shape];
}

@end
