// Copyright (c) 2020 Vivaldi Technologies AS. All rights reserved

#ifndef PLATFORM_MEDIA_IPC_DEMUXER_GPU_PIPELINE_IPC_DECODING_BUFFER_H_
#define PLATFORM_MEDIA_IPC_DEMUXER_GPU_PIPELINE_IPC_DECODING_BUFFER_H_

#include "platform_media/ipc_demuxer/platform_media_pipeline_types.h"

#include "chromium/base/callback.h"
#include "chromium/base/memory/read_only_shared_memory_region.h"

#include <memory>

namespace media {

// The shared memory buffer for media data of a particular type. The pipeline
// moves the instance of the class into the decoder that fills it with a new
// video frame or audio sample and then moves back to the pipeline to notify the
// renderer process that the new data are available.
class MEDIA_EXPORT IPCDecodingBuffer {
 public:
  using ReplyCB = base::OnceCallback<void(IPCDecodingBuffer buffer)>;

  IPCDecodingBuffer();
  IPCDecodingBuffer(IPCDecodingBuffer&&);
  ~IPCDecodingBuffer();
  IPCDecodingBuffer& operator=(IPCDecodingBuffer&&);

  explicit operator bool() const { return !!impl_; }

  void Init(PlatformStreamType stream_type);

  PlatformStreamType stream_type() const { return impl_->stream_type; }

  void set_reply_cb(ReplyCB reply_cb) { impl_->reply_cb = std::move(reply_cb); }

  // Send the buffer back to the pipeline to notify about a new media sample
  // available.
  static void Reply(IPCDecodingBuffer buffer);

  MediaDataStatus status() const { return impl_->status; }
  void set_status(MediaDataStatus status) { impl_->status = status; }

  base::TimeDelta timestamp() const { return impl_->timestamp; }
  void set_timestamp(base::TimeDelta timestamp) {
    impl_->timestamp = timestamp;
  }

  base::TimeDelta duration() const { return impl_->duration; }
  void set_duration(base::TimeDelta duration) { impl_->duration = duration; }

  int data_size() const { return impl_->data_size; }

  // Ensure that the shared memory can hold at least data_size bytes and return
  // a pointer to copy the decoded data into. Return nullptr on errors.
  uint8_t* PrepareMemory(size_t data_size);

  // Moves out the new region that SetData created if any to notify the renderer
  // process about the region change.
  base::ReadOnlySharedMemoryRegion extract_region_to_send() {
    return std::move(impl_->region);
  }

  PlatformAudioConfig& GetAudioConfig();

  PlatformVideoConfig& GetVideoConfig();

  // Access the buffer from tests.
  const uint8_t* GetDataForTests() const;

 private:
  // To make the move cheap the class stores all its fields in unique_ptr.
  struct Impl {
    Impl(PlatformStreamType stream_type);
    ~Impl();
    ReplyCB reply_cb;
    const PlatformStreamType stream_type;
    MediaDataStatus status = MediaDataStatus::kOk;
    int data_size = 0;
    base::TimeDelta timestamp;
    base::TimeDelta duration;
    base::WritableSharedMemoryMapping mapping;
    base::ReadOnlySharedMemoryRegion region;

    std::unique_ptr<PlatformAudioConfig> audio_config;
    std::unique_ptr<PlatformVideoConfig> video_config;
  };
  std::unique_ptr<Impl> impl_;
};

}  // namespace media

#endif  // PLATFORM_MEDIA_IPC_DEMUXER_GPU_PIPELINE_IPC_DECODING_BUFFER_H_
