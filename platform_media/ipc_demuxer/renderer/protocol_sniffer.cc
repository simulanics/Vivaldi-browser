// -*- Mode: c++; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
//
// Copyright (c) 2018 Vivaldi Technologies AS. All rights reserved.
// Copyright (C) 2014 Opera Software ASA.  All rights reserved.
//
// This file is an original work developed by Opera Software ASA

#include "platform_media/ipc_demuxer/renderer/protocol_sniffer.h"

#include "base/bind.h"
#include "base/logging.h"
#include "build/build_config.h"
#include "media/base/container_names.h"
#include "media/base/data_source.h"

#include "platform_media/ipc_demuxer/renderer/ipc_demuxer.h"

namespace media {

namespace {

std::string GetContainerName(container_names::MediaContainerName container) {
  switch (container) {
    case container_names::CONTAINER_UNKNOWN:
      return "Unknown";
    case container_names::CONTAINER_AAC:
      return "AAC (Advanced Audio Coding)";
    case container_names::CONTAINER_AC3:
      return "AC-3";
    case container_names::CONTAINER_AIFF:
      return "AIFF (Audio Interchange File Format)";
    case container_names::CONTAINER_AMR:
      return "AMR (Adaptive Multi-Rate Audio)";
    case container_names::CONTAINER_APE:
      return "APE (Monkey's Audio)";
    case container_names::CONTAINER_ASF:
      return "ASF (Advanced / Active Streaming Format)";
    case container_names::CONTAINER_ASS:
      return "SSA (SubStation Alpha) subtitle";
    case container_names::CONTAINER_AVI:
      return "AVI (Audio Video Interleaved)";
    case container_names::CONTAINER_BINK:
      return "Bink";
    case container_names::CONTAINER_CAF:
      return "CAF (Apple Core Audio Format)";
    case container_names::CONTAINER_DTS:
      return "DTS";
    case container_names::CONTAINER_DTSHD:
      return "DTS-HD";
    case container_names::CONTAINER_DV:
      return "DV (Digital Video)";
    case container_names::CONTAINER_DXA:
      return "DXA";
    case container_names::CONTAINER_EAC3:
      return "Enhanced AC-3";
    case container_names::CONTAINER_FLAC:
      return "FLAC (Free Lossless Audio Codec)";
    case container_names::CONTAINER_FLV:
      return "FLV (Flash Video)";
    case container_names::CONTAINER_GSM:
      return "GSM (Global System for Mobile Audio)";
    case container_names::CONTAINER_H261:
      return "H.261";
    case container_names::CONTAINER_H263:
      return "H.263";
    case container_names::CONTAINER_H264:
      return "H.264";
    case container_names::CONTAINER_HLS:
      return "HLS (Apple HTTP Live Streaming PlayList)";
    case container_names::CONTAINER_IRCAM:
      return "Berkeley/IRCAM/CARL Sound Format";
    case container_names::CONTAINER_MJPEG:
      return "MJPEG video";
    case container_names::CONTAINER_MOV:
      return "QuickTime / MOV / MPEG4";
    case container_names::CONTAINER_MP3:
      return "MP3 (MPEG audio layer 2/3)";
    case container_names::CONTAINER_MPEG2PS:
      return "MPEG-2 Program Stream";
    case container_names::CONTAINER_MPEG2TS:
      return "MPEG-2 Transport Stream";
    case container_names::CONTAINER_MPEG4BS:
      return "MPEG-4 Bitstream";
    case container_names::CONTAINER_OGG:
      return "Ogg";
    case container_names::CONTAINER_RM:
      return "RM (RealMedia)";
    case container_names::CONTAINER_SRT:
      return "SRT (SubRip subtitle)";
    case container_names::CONTAINER_SWF:
      return "SWF (ShockWave Flash)";
    case container_names::CONTAINER_VC1:
      return "VC-1";
    case container_names::CONTAINER_WAV:
      return "WAV / WAVE (Waveform Audio)";
    case container_names::CONTAINER_WEBM:
      return "Matroska / WebM";
    case container_names::CONTAINER_WTV:
      return "WTV (Windows Television)";
    case container_names::CONTAINER_DASH:
      return "DASH (MPEG-DASH)";
    case container_names::CONTAINER_SMOOTHSTREAM:
      return "SmoothStreaming";
  }
  NOTREACHED();
  return "";
}

std::string DetermineContainer(const uint8_t* data, size_t data_size) {
  const container_names::MediaContainerName container =
      container_names::OperaDetermineContainer(data, data_size);

  switch (container) {
    case container_names::CONTAINER_AAC:
      return "audio/aac";

    case container_names::CONTAINER_WAV:
      return "audio/wav";

    case container_names::CONTAINER_H264:
      return "video/mp4";

    default:
      VLOG(1) << " PROPMEDIA(RENDERER) : " << __FUNCTION__
              << " Ignored container : " << GetContainerName(container);
      break;
  }

  return std::string();
}

void SniffReadDone(std::unique_ptr<uint8_t[]> data,
                   ProtocolSniffer::SniffProtocolResult callback,
                   int size_read) {
  std::string mime_type;
  if (size_read > 0) {
    mime_type = DetermineContainer(data.get(), size_read);
  }

  VLOG(1) << " PROPMEDIA(RENDERER) : " << __FUNCTION__
          << " sniffed MimeType : '" << mime_type << "'";

  std::move(callback).Run(std::move(mime_type));
}

}  // namespace

// static
bool ProtocolSniffer::ShouldSniffProtocol(const std::string& content_type) {
  bool should_sniff = !IPCDemuxer::CanPlayType(content_type);
#if BUILDFLAG(IS_MAC)
  // NOTE(jarle@vivalid.com): We cannot trust this mime type. Use the sniffer
  // to determine the content type, so the correct decoder can be selected.
  // Ref. VB-40530, VB-43812.
  if (!should_sniff && (content_type == "video/mp4")) {
    VLOG(1) << " PROPMEDIA(RENDERER) : " << __FUNCTION__
            << " overriding sniff decision";
    should_sniff = true;
  }
#endif
  VLOG(1) << " PROPMEDIA(RENDERER) : " << __FUNCTION__ << " sniff MimeType : '"
          << content_type << "' : " << (should_sniff ? "Yes" : "No");

  return should_sniff;
}

// static
void ProtocolSniffer::SniffProtocol(DataSource* data_source,
                                    SniffProtocolResult callback) {
  DCHECK(data_source);

  // We read the first 8192 bytes, same as FFmpeg.
  static const size_t kDataSize = 8192;
  std::unique_ptr<uint8_t[]> data_holder(new uint8_t[kDataSize]);

  // C++ does not define the order of argument evaluation, so get the pointer
  // before base::BindOnce() can be evaluated and call the move constructor for
  // data_holder.
  uint8_t* data = data_holder.get();
  data_source->Read(0, kDataSize, data,
                    base::BindOnce(&SniffReadDone, std::move(data_holder),
                                   std::move(callback)));
}

}  // namespace media
