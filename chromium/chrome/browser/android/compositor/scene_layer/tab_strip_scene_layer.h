// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_ANDROID_COMPOSITOR_SCENE_LAYER_TAB_STRIP_SCENE_LAYER_H_
#define CHROME_BROWSER_ANDROID_COMPOSITOR_SCENE_LAYER_TAB_STRIP_SCENE_LAYER_H_

#include <memory>
#include <vector>

#include "base/android/jni_android.h"
#include "base/android/jni_weak_ref.h"
#include "base/android/scoped_java_ref.h"
#include "base/memory/raw_ptr.h"
#include "cc/layers/layer.h"
#include "cc/layers/ui_resource_layer.h"
#include "chrome/browser/ui/android/layouts/scene_layer.h"

namespace cc {
class SolidColorLayer;
}

namespace android {

class LayerTitleCache;
class TabHandleLayer;

// A scene layer to draw one or more tab strips. Note that content tree can be
// added as a subtree.
class TabStripSceneLayer : public SceneLayer {
 public:
  TabStripSceneLayer(JNIEnv* env, const base::android::JavaRef<jobject>& jobj);

  TabStripSceneLayer(const TabStripSceneLayer&) = delete;
  TabStripSceneLayer& operator=(const TabStripSceneLayer&) = delete;

  ~TabStripSceneLayer() override;

  void SetContentTree(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      const base::android::JavaParamRef<jobject>& jcontent_tree);

  void BeginBuildingFrame(JNIEnv* env,
                          const base::android::JavaParamRef<jobject>& jobj,
                          jboolean visible);

  void FinishBuildingFrame(JNIEnv* env,
                           const base::android::JavaParamRef<jobject>& jobj);

  void UpdateTabStripLayer(JNIEnv* env,
                           const base::android::JavaParamRef<jobject>& jobj,
                           jfloat width,
                           jfloat height,
                           jfloat y_offset,
                           jboolean should_readd_background);

  void UpdateStripScrim(JNIEnv* env,
                        const base::android::JavaParamRef<jobject>& jobj,
                        jfloat x,
                        jfloat y,
                        jfloat width,
                        jfloat height,
                        jint color,
                        jfloat alpha);

  void UpdateNewTabButton(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      jint resource_id,
      jfloat x,
      jfloat y,
      jfloat width,
      jfloat height,
      jfloat touch_target_offset,
      jboolean visible,
      jint tint,
      jfloat button_alpha,
      const base::android::JavaParamRef<jobject>& jresource_manager);

  void UpdateModelSelectorButton(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      jint resource_id,
      jfloat x,
      jfloat y,
      jfloat width,
      jfloat height,
      jboolean incognito,
      jboolean visible,
      const base::android::JavaParamRef<jobject>& jresource_manager);

  void UpdateTabStripLeftFade(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      jint resource_id,
      jfloat opacity,
      const base::android::JavaParamRef<jobject>& jresource_manager);

  void UpdateTabStripRightFade(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      jint resource_id,
      jfloat opacity,
      const base::android::JavaParamRef<jobject>& jresource_manager);

  void PutStripTabLayer(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      jint id,
      jint close_resource_id,
      jint handle_resource_id,
      jint handle_outline_resource_id,
      jint close_tint,
      jint handle_tint,
      jint handle_outline_tint,
      jboolean foreground,
      jboolean close_pressed,
      jfloat toolbar_width,
      jfloat x,
      jfloat y,
      jfloat width,
      jfloat height,
      jfloat content_offset_x,
      jfloat close_button_alpha,
      jboolean is_loading,
      jfloat spinner_rotation,
      jfloat brightness,
      const base::android::JavaParamRef<jobject>& jlayer_title_cache,
      const base::android::JavaParamRef<jobject>& jresource_manager,
      jfloat tab_alpha, // Vivaldi
      jboolean is_shown_as_favicon, // Vivaldi
      jfloat title_offset); // Vivaldi

  bool ShouldShowBackground() override;
  SkColor GetBackgroundColor() override;

  // Vivaldi
  void SetTabStripBackgroundColor(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      jint java_color,
      jboolean use_light);

  // Vivaldi
  void SetIsStackStrip(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      jboolean is_stack_strip);

  // Vivaldi
  void UpdateLoadingState(
      JNIEnv* env,
      const base::android::JavaParamRef<jobject>& jobj,
      jint loading_text_resource_id,
      const base::android::JavaParamRef<jobject>& jresource_manager,
      jboolean should_show_loading);

 private:
  scoped_refptr<TabHandleLayer> GetNextLayer(
      LayerTitleCache* layer_title_cache);

  typedef std::vector<scoped_refptr<TabHandleLayer>> TabHandleLayerList;

  scoped_refptr<cc::SolidColorLayer> tab_strip_layer_;
  scoped_refptr<cc::Layer> scrollable_strip_layer_;
  scoped_refptr<cc::SolidColorLayer> scrim_layer_;
  scoped_refptr<cc::UIResourceLayer> new_tab_button_;
  scoped_refptr<cc::UIResourceLayer> left_fade_;
  scoped_refptr<cc::UIResourceLayer> right_fade_;
  scoped_refptr<cc::UIResourceLayer> model_selector_button_;

  unsigned write_index_;
  TabHandleLayerList tab_handle_layers_;
  raw_ptr<SceneLayer> content_tree_;

  // Vivaldi
  bool use_light_foreground_on_background;
  bool is_stack_strip_;
  scoped_refptr<cc::UIResourceLayer> loading_text_;
};

}  // namespace android

#endif  // CHROME_BROWSER_ANDROID_COMPOSITOR_SCENE_LAYER_TAB_STRIP_SCENE_LAYER_H_
