// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/infobars/modals/infobar_modal_view_controller.h"

#import "ios/chrome/browser/ui/icons/chrome_symbol.h"
#import "ios/chrome/browser/ui/icons/infobar_icon.h"
#import "ios/chrome/browser/ui/infobars/modals/infobar_modal_constants.h"
#import "ios/chrome/browser/ui/infobars/modals/infobar_modal_delegate.h"
#import "ios/chrome/common/ui/colors/semantic_color_names.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

@interface InfobarModalViewController ()

@property(strong, nonatomic) id<InfobarModalDelegate> infobarModalDelegate;

@end

@implementation InfobarModalViewController

- (instancetype)initWithModalDelegate:
    (id<InfobarModalDelegate>)infobarModalDelegate {
  self = [super initWithNibName:nil bundle:nil];
  if (self) {
    _infobarModalDelegate = infobarModalDelegate;
  }
  return self;
}

#pragma mark - View Lifecycle

// TODO(crbug.com/1372916): PLACEHOLDER UI for the modal ViewController.
- (void)viewDidLoad {
  [super viewDidLoad];
  self.view.backgroundColor = [UIColor colorNamed:kBackgroundColor];

  // Configure the NavigationBar.
  UIBarButtonItem* cancelButton = [[UIBarButtonItem alloc]
      initWithBarButtonSystemItem:UIBarButtonSystemItemCancel
                           target:self.infobarModalDelegate
                           action:@selector(dismissInfobarModal:)];
  cancelButton.accessibilityIdentifier = kInfobarModalCancelButton;
  UIImage* gearImage = UseSymbols()
                           ? DefaultSymbolWithPointSize(kSettingsFilledSymbol,
                                                        kSymbolImagePointSize)
                           : [UIImage imageNamed:@"infobar_settings_icon"];
  gearImage =
      [gearImage imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
  UIBarButtonItem* settingsButton =
      [[UIBarButtonItem alloc] initWithImage:gearImage
                                       style:UIBarButtonItemStylePlain
                                      target:self
                                      action:nil];
  self.navigationItem.leftBarButtonItem = cancelButton;
  self.navigationItem.rightBarButtonItem = settingsButton;
}

- (void)viewDidDisappear:(BOOL)animated {
  [self.infobarModalDelegate modalInfobarWasDismissed:self];
  [super viewDidDisappear:animated];
}

@end
