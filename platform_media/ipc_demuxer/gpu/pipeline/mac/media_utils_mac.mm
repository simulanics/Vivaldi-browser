// -*- Mode: c++; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
//
// Copyright (c) 2018 Vivaldi Technologies AS. All rights reserved.
// Copyright (C) 2014 Opera Software ASA.  All rights reserved.
//
// This file is an original work developed by Opera Software ASA

#include "platform_media/ipc_demuxer/gpu/pipeline/mac/media_utils_mac.h"

#import <AVFoundation/AVFoundation.h>
#include <CoreAudio/CoreAudio.h>
#import <Foundation/Foundation.h>

#include "base/logging.h"
#include "base/stl_util.h"

#include "platform_media/ipc_demuxer/mac/framework_type_conversions.h"

namespace media {

namespace {

VideoRotation AffineTransformToVideoRotation(
    const CGAffineTransform& transform,
    const gfx::Size& size) {
  // See CGAffineTransform documentation for the meaning of the transformation
  // matrix.
  const struct {
    CGAffineTransform transform;
    VideoRotation rotation;
  } kRotationMap[] = {
      {{1, 0, 0, 1, 0, 0}, VideoRotation::VIDEO_ROTATION_0},
      {{0, 1, -1, 0, static_cast<CGFloat>(size.height()), 0},
       VideoRotation::VIDEO_ROTATION_90},
      {{-1, 0, 0, -1, static_cast<CGFloat>(size.width()),
        static_cast<CGFloat>(size.height())},
       VideoRotation::VIDEO_ROTATION_180},
      {{-1, 0, 0, -1, 0, 0}, VideoRotation::VIDEO_ROTATION_180},
      {{0, -1, 1, 0, 0, static_cast<CGFloat>(size.width())},
       VideoRotation::VIDEO_ROTATION_270},
  };

  for (size_t i = 0; i < std::size(kRotationMap); ++i) {
    if (CGAffineTransformEqualToTransform(transform, kRotationMap[i].transform))
      return kRotationMap[i].rotation;
  }

  LOG(WARNING) << " PROPMEDIA(COMMON) : " << __FUNCTION__
               << " Unsupported affine transform, ignoring";
  return VideoRotation::VIDEO_ROTATION_0;
}

}  // namespace

base::TimeDelta GetStartTimeFromTrack(AVAssetTrack* track) {
  base::TimeDelta start_time;

  for (AVAssetTrackSegment* segment in [track segments]) {
    const CMTimeMapping mapping = [segment timeMapping];
    // The start time is determined by the first valid track segment.
    if (CMTIME_IS_VALID(mapping.source.start) &&
        CMTIME_IS_VALID(mapping.target.start)) {
      start_time = CMTimeToTimeDelta(mapping.target.start) -
                   CMTimeToTimeDelta(mapping.source.start);
      break;
    }
  }

  return start_time;
}

PlatformVideoConfig GetPlatformVideoConfig(CMFormatDescriptionRef description,
                                           CGAffineTransform transform,
                                           size_t stride_Y,
                                           size_t stride_UV) {
  VLOG(4) << " PROPMEDIA(COMMON) : " << __FUNCTION__;
  PlatformVideoConfig video_config;

  const CMVideoDimensions coded_size =
      CMVideoFormatDescriptionGetDimensions(description);
  video_config.coded_size = gfx::Size(coded_size.width, coded_size.height);

  video_config.visible_rect = gfx::Rect(
      CMVideoFormatDescriptionGetCleanAperture(description, NO));

  video_config.natural_size =
      gfx::Size(CMVideoFormatDescriptionGetPresentationDimensions(
          description, YES, NO));

  // An even width/height makes things easier for YV12 and appears to be the
  // behavior expected by WebKit layout tests.
  video_config.natural_size =
      gfx::Size(video_config.natural_size.width() & ~1,
                video_config.natural_size.height() & ~1);

  // TODO(mkoss): Tentative implementation of stride calculation based on width.
  // The stride must be a multiple of 32 for each plane.
  video_config.planes[VideoFrame::kYPlane].stride = stride_Y != 0 ? stride_Y :
      (video_config.coded_size.width() + 31) & ~31;
  video_config.planes[VideoFrame::kVPlane].stride = stride_UV != 0 ? stride_UV :
      (video_config.planes[VideoFrame::kYPlane].stride / 2 + 31) & ~31;
  video_config.planes[VideoFrame::kUPlane].stride =
      video_config.planes[VideoFrame::kVPlane].stride;

  int rows = video_config.coded_size.height();

  // Y plane is first and is not downsampled.
  video_config.planes[VideoFrame::kYPlane].offset = 0;
  video_config.planes[VideoFrame::kYPlane].size =
      rows * video_config.planes[VideoFrame::kYPlane].stride;

  // In YV12, V and U planes are downsampled vertically and horizontally by 2.
  rows /= 2;

  // V plane precedes U.
  video_config.planes[VideoFrame::kVPlane].offset =
      video_config.planes[VideoFrame::kYPlane].offset +
      video_config.planes[VideoFrame::kYPlane].size;
  video_config.planes[VideoFrame::kVPlane].size =
      rows * video_config.planes[VideoFrame::kVPlane].stride;

  video_config.planes[VideoFrame::kUPlane].offset =
      video_config.planes[VideoFrame::kVPlane].offset +
      video_config.planes[VideoFrame::kVPlane].size;
  video_config.planes[VideoFrame::kUPlane].size =
      rows * video_config.planes[VideoFrame::kUPlane].stride;

  video_config.rotation = AffineTransformToVideoRotation(
      transform,
      gfx::Size(
          CMVideoFormatDescriptionGetPresentationDimensions(
              description, YES, NO)));


  return video_config;
}

}  // namespace media
