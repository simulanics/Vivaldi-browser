// -*- Mode: c++; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
//
// Copyright (c) 2018 Vivaldi Technologies AS. All rights reserved.
// Copyright (C) 2014 Opera Software ASA.  All rights reserved.
//
// This file is an original work developed by Opera Software ASA

#ifndef PLATFORM_MEDIA_IPC_DEMUXER_GPU_PIPELINE_PLATFORM_MEDIA_PIPELINE_H_
#define PLATFORM_MEDIA_IPC_DEMUXER_GPU_PIPELINE_PLATFORM_MEDIA_PIPELINE_H_

#include <string>

#include "base/callback.h"
#include "base/memory/ref_counted.h"
#include "base/time/time.h"

#include "platform_media/ipc_demuxer/gpu/data_source/ipc_data_source.h"
#include "platform_media/ipc_demuxer/gpu/pipeline/ipc_decoding_buffer.h"
#include "platform_media/ipc_demuxer/platform_media.mojom.h"

namespace media {

class DataBuffer;
struct PlatformAudioConfig;
struct PlatformVideoConfig;

// An interface for the media pipeline using decoder infrastructure available
// on specific platforms.  It represents a full decoding pipeline - it reads
// raw input data from a DataSource and outputs decoded and properly formatted
// audio and/or video samples.
class PlatformMediaPipeline {
 public:
  using InitializeCB =
      base::OnceCallback<void(platform_media::mojom::PipelineInitResultPtr)>;
  using SeekCB = base::OnceCallback<void(bool success)>;

  // The implementation is in a platform-specific file.
  static std::unique_ptr<PlatformMediaPipeline> Create();

  virtual ~PlatformMediaPipeline() {}

  virtual void Initialize(ipc_data_source::Info source_info,
                          InitializeCB initialize_cb) = 0;

  // Read the media data of the given type into the supplied buffer.
  // When done with decoding, the code must call IPCDecodingBuffer::Reply()
  // on the passed in buffer even on errors.
  virtual void ReadMediaData(IPCDecodingBuffer buffer) = 0;

  virtual void WillSeek() = 0;
  virtual void Seek(base::TimeDelta time, SeekCB seek_cb) = 0;
};

}  // namespace media

#endif  // PLATFORM_MEDIA_IPC_DEMUXER_GPU_PIPELINE_PLATFORM_MEDIA_PIPELINE_H_
