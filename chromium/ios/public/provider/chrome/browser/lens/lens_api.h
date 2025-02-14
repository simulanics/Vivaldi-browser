// Copyright 2021 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef IOS_PUBLIC_PROVIDER_CHROME_BROWSER_LENS_LENS_API_H_
#define IOS_PUBLIC_PROVIDER_CHROME_BROWSER_LENS_LENS_API_H_

#import <UIKit/UIKit.h>

#import "base/callback.h"
#import "ios/web/public/navigation/navigation_manager.h"

@class LensConfiguration;
@class UIViewController;
class GURL;
enum class LensEntrypoint;

// A delegate that can receive Lens events forwarded by a ChromeLensController.
@protocol ChromeLensControllerDelegate <NSObject>

// Called when the Lens view controller's dimiss button has been tapped.
- (void)lensControllerDidTapDismissButton;

// Called when the user selects a URL in Lens.
- (void)lensControllerDidSelectURL:(NSURL*)url;

// Called when the user selects an image and the Lens controller has prepared
// `params` for loading a Lens web page.
- (void)lensControllerDidGenerateLoadParams:
    (const web::NavigationManager::WebLoadParams&)params;

@end

// A controller that can facilitate communication with the downstream Lens
// controller.
@protocol ChromeLensController <NSObject>

// A delegate that can receive Lens events forwarded by the controller.
@property(nonatomic, weak) id<ChromeLensControllerDelegate> delegate;

// Returns an input selection UIViewController with the provided
// web content frame.
- (UIViewController*)inputSelectionViewControllerWithWebContentFrame:
    (CGRect)webContentFrame;

@end

namespace ios {
namespace provider {

// Callback invoked when the web load params for a Lens query have been
// generated.
using LensWebParamsCallback =
    base::OnceCallback<void(web::NavigationManager::WebLoadParams)>;

// Returns a controller for the given configuration that can facilitate
// communication with the downstream Lens controller.
id<ChromeLensController> NewChromeLensController(LensConfiguration* config);

// Returns whether Lens is supported for the current build.
bool IsLensSupported();

// Returns whether or not the url represents a Lens Web results page.
bool IsLensWebResultsURL(const GURL& url);

// Generates web load params for a Lens image search for the given
// 'image' and 'entry_point'.
web::NavigationManager::WebLoadParams GenerateLensLoadParamsForImage(
    UIImage* image,
    LensEntrypoint entry_point,
    bool is_incognito);

// Generates web load params for a Lens image search for the given
// 'image' and 'entry_point'. `completion` will be run on the main
// thread.
void GenerateLensLoadParamsForImageAsync(UIImage* image,
                                         LensEntrypoint entry_point,
                                         bool is_incognito,
                                         LensWebParamsCallback completion);

}  // namespace provider
}  // namespace ios

#endif  // IOS_PUBLIC_PROVIDER_CHROME_BROWSER_LENS_LENS_API_H_
