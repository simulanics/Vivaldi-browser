// Copyright (c) 2021 Vivaldi Technologies AS. All rights reserved.

#ifndef PLATFORM_MEDIA_IPC_DEMUXER_RENDERER_PASS_THROUGH_DECODER_H_
#define PLATFORM_MEDIA_IPC_DEMUXER_RENDERER_PASS_THROUGH_DECODER_H_

#include "media/filters/decoder_stream_traits.h"

namespace media {

template <DemuxerStream::Type StreamType>
std::unique_ptr<typename DecoderStreamTraits<StreamType>::DecoderType>
CreatePlatformMediaPassThroughDecoder();

}  // namespace media

#endif  // PLATFORM_MEDIA_IPC_DEMUXER_RENDERER_PASS_THROUGH_DECODER_H_
