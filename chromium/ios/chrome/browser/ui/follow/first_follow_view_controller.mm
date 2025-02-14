// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/follow/first_follow_view_controller.h"

#import "base/strings/sys_string_conversions.h"
#import "ios/chrome/browser/ui/follow/followed_web_channel.h"
#import "ios/chrome/browser/ui/icons/chrome_symbol.h"
#import "ios/chrome/common/ui/colors/semantic_color_names.h"
#import "ios/chrome/common/ui/favicon/favicon_container_view.h"
#import "ios/chrome/common/ui/favicon/favicon_view.h"
#import "ios/chrome/common/ui/util/button_util.h"
#import "ios/chrome/common/ui/util/constraints_ui_util.h"
#import "ios/chrome/grit/ios_strings.h"
#import "ui/base/l10n/l10n_util_mac.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {

constexpr CGFloat customSpacingBeforeImageIfNoToolbar = 24;
constexpr CGFloat customSpacingAfterImage = 24;

}  // namespace

@implementation FirstFollowViewController {
  std::u16string _webSiteTitle;
  BOOL _webSiteIsAvailable;
  FirstFollowFaviconSource _faviconSource;
}

- (instancetype)initWithTitle:(NSString*)title
                    available:(BOOL)available
                faviconSource:(FirstFollowFaviconSource)faviconSource {
  if ((self = [super initWithNibName:nil bundle:nil])) {
    _webSiteTitle = base::SysNSStringToUTF16(title);
    _webSiteIsAvailable = available;
    _faviconSource = faviconSource;
  }
  return self;
}

- (void)viewDidLoad {
  self.imageHasFixedSize = YES;
  self.imageEnclosedWithShadowAndBadge = YES;
  self.showDismissBarButton = NO;
  self.customSpacingBeforeImageIfNoToolbar =
      customSpacingBeforeImageIfNoToolbar;
  self.customSpacingAfterImage = customSpacingAfterImage;
  self.titleTextStyle = UIFontTextStyleTitle2;
  self.topAlignedLayout = YES;

  self.titleString =
      l10n_util::GetNSStringF(IDS_IOS_FIRST_FOLLOW_TITLE, _webSiteTitle);
  self.secondaryTitleString =
      l10n_util::GetNSStringF(IDS_IOS_FIRST_FOLLOW_SUBTITLE, _webSiteTitle);
  self.subtitleString = l10n_util::GetNSString(IDS_IOS_FIRST_FOLLOW_BODY);

  if (_webSiteIsAvailable) {
    // Go To Feed button is only displayed if the web channel is available.
    self.primaryActionString =
        l10n_util::GetNSString(IDS_IOS_FIRST_FOLLOW_GO_TO_FEED);
    self.secondaryActionString =
        l10n_util::GetNSString(IDS_IOS_FIRST_FOLLOW_GOT_IT);
  } else {
    // Only one button is visible, and it is a primary action button (with a
    // solid background color).
    self.primaryActionString =
        l10n_util::GetNSString(IDS_IOS_FIRST_FOLLOW_GOT_IT);
  }

  // TODO(crbug.com/1312124): Favicon styling needs more whitespace, shadow, and
  // corner green checkmark badge.
  if (_faviconSource) {
    __weak __typeof(self) weakSelf = self;
    _faviconSource(^(UIImage* favicon) {
      weakSelf.image = favicon;
    });
  }

  [super viewDidLoad];
}

#pragma mark - ConfirmationAlertViewController

- (void)updateStylingForSecondaryTitleLabel:(UILabel*)secondaryTitleLabel {
  secondaryTitleLabel.font =
      [UIFont preferredFontForTextStyle:UIFontTextStyleBody];
  secondaryTitleLabel.textColor = [UIColor colorNamed:kTextSecondaryColor];
}

- (void)updateStylingForSubtitleLabel:(UILabel*)subtitleLabel {
  subtitleLabel.font =
      [UIFont preferredFontForTextStyle:UIFontTextStyleFootnote];
  subtitleLabel.textColor = [UIColor colorNamed:kTextTertiaryColor];
}

@end
