// -*- Mode: c++; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
//
// Copyright (c) 2018 Vivaldi Technologies AS. All rights reserved.
// Copyright (C) 2015 Opera Software ASA.  All rights reserved.
//
// This file is based on original work developed by Opera Software ASA.

#include "platform_media/ipc_demuxer/renderer/video_frame_transformer.h"

#include "base/bind.h"
#include "base/logging.h"
#include "media/base/decoder_buffer.h"
#include "media/base/video_decoder_config.h"
#include "media/base/video_frame.h"

#include "platform_media/ipc_demuxer/platform_media_pipeline_types.h"

namespace media {

namespace {

// This function is used as observer of frame of
// VideoFrame::WrapExternalYuvData to make sure we keep reference to
// DecoderBuffer object as long as we need it.
void BufferHolder(scoped_refptr<DecoderBuffer> buffer) {
  /* Intentionally empty */
}

const PlatformVideoConfig::Plane* GetPlanes(const VideoDecoderConfig& config) {
  DCHECK(!config.extra_data().empty());
  return reinterpret_cast<const PlatformVideoConfig::Plane*>(
      &config.extra_data().front());
}

}  // namespace

scoped_refptr<VideoFrame> GetVideoFrameFromMemory(
    scoped_refptr<DecoderBuffer> buffer,
    const VideoDecoderConfig& config) {
  if (buffer->end_of_stream())
    return scoped_refptr<VideoFrame>();

  const PlatformVideoConfig::Plane* planes = GetPlanes(config);

  for (size_t i = 0;
       i < VideoFrame::NumPlanes(VideoPixelFormat::PIXEL_FORMAT_YV12); ++i) {
    if (planes[i].offset + planes[i].size > int(buffer->data_size())) {
      LOG(ERROR) << " PROPMEDIA(RENDERER) : " << __FUNCTION__
                 << " Buffer doesn't match video format";
      return nullptr;
    }
  }

  // We need to ensure that our data buffer stays valid long enough.
  // For this reason we pass it as an argument to |no_longer_needed_cb|, this
  // way, thanks to base::Bind, we keep reference to the buffer.
  scoped_refptr<VideoFrame> frame = VideoFrame::WrapExternalYuvData(
      VideoPixelFormat::PIXEL_FORMAT_YV12, config.coded_size(),
      config.visible_rect(), config.natural_size(),
      planes[VideoFrame::kYPlane].stride, planes[VideoFrame::kUPlane].stride,
      planes[VideoFrame::kVPlane].stride,
      const_cast<uint8_t*>(buffer->data() + planes[VideoFrame::kYPlane].offset),
      const_cast<uint8_t*>(buffer->data() + planes[VideoFrame::kUPlane].offset),
      const_cast<uint8_t*>(buffer->data() + planes[VideoFrame::kVPlane].offset),
      buffer->timestamp());
  frame->AddDestructionObserver(
      base::BindOnce(&BufferHolder, std::move(buffer)));
  return frame;
}

}  // namespace media
