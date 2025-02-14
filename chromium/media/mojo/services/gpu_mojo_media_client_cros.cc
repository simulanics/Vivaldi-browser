// Copyright 2021 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "media/mojo/services/gpu_mojo_media_client.h"

#include "base/metrics/histogram_functions.h"
#include "build/build_config.h"
#include "media/base/audio_decoder.h"
#include "media/base/audio_encoder.h"
#include "media/base/media_switches.h"
#include "media/gpu/chromeos/mailbox_video_frame_converter.h"
#include "media/gpu/chromeos/platform_video_frame_pool.h"
#include "media/gpu/chromeos/video_decoder_pipeline.h"
#include "media/gpu/ipc/service/vda_video_decoder.h"

#if BUILDFLAG(IS_CHROMEOS)
#include "chromeos/components/cdm_factory_daemon/chromeos_cdm_factory.h"
#endif  // BUILDFLAG(IS_CHROMEOS)

namespace media {

namespace {

#if BUILDFLAG(IS_CHROMEOS)

VideoDecoderType GetPreferredCrosDecoderImplementation(
    gpu::GpuPreferences gpu_preferences) {
  // TODO(b/195769334): eventually, we may turn off USE_VAAPI and USE_V4L2_CODEC
  // on LaCrOS if we delegate all video acceleration to ash-chrome. In those
  // cases, GetPreferredCrosDecoderImplementation() won't be able to determine
  // the video API in LaCrOS.
  if (gpu_preferences.disable_accelerated_video_decode)
    return VideoDecoderType::kUnknown;

  if (gpu_preferences.enable_chromeos_direct_video_decoder) {
#if BUILDFLAG(USE_VAAPI)
    return VideoDecoderType::kVaapi;
#elif BUILDFLAG(USE_V4L2_CODEC)
    return VideoDecoderType::kV4L2;
#endif
  }
  return VideoDecoderType::kVda;
}

#else

VideoDecoderType GetPreferredLinuxDecoderImplementation(
    gpu::GpuPreferences gpu_preferences,
    const gpu::GPUInfo& gpu_info) {
  // VaapiVideoDecoder flag is required for both VDA and VaapiVideoDecoder.
  if (!base::FeatureList::IsEnabled(kVaapiVideoDecodeLinux))
    return VideoDecoderType::kUnknown;

  // If direct video decoder is disabled, revert to using the VDA
  // implementation.
  if (!base::FeatureList::IsEnabled(kUseChromeOSDirectVideoDecoder))
    return VideoDecoderType::kVda;
  return VideoDecoderType::kVaapi;
}

#endif  // BUILDFLAG(IS_CHROMEOS)

VideoDecoderType GetActualPlatformDecoderImplementation(
    gpu::GpuPreferences gpu_preferences,
    const gpu::GPUInfo& gpu_info) {
#if BUILDFLAG(IS_CHROMEOS)
  return GetPreferredCrosDecoderImplementation(gpu_preferences);
#else
  // On linux, VDA and Vaapi have GL restrictions.
  switch (GetPreferredLinuxDecoderImplementation(gpu_preferences, gpu_info)) {
    case VideoDecoderType::kUnknown:
      return VideoDecoderType::kUnknown;
    case VideoDecoderType::kVda: {
      return gpu_preferences.gr_context_type == gpu::GrContextType::kGL
                 ? VideoDecoderType::kVda
                 : VideoDecoderType::kUnknown;
    }
    case VideoDecoderType::kVaapi: {
      // Allow VaapiVideoDecoder on GL.
      if (gpu_preferences.gr_context_type == gpu::GrContextType::kGL)
        return VideoDecoderType::kVaapi;
      if (gpu_preferences.gr_context_type != gpu::GrContextType::kVulkan)
        return VideoDecoderType::kUnknown;
      // If Vulkan is active, check Vulkan info if VaapiVideoDecoder is allowed.
      if (!gpu_info.vulkan_info.has_value())
        return VideoDecoderType::kUnknown;
      if (gpu_info.vulkan_info->physical_devices.empty())
        return VideoDecoderType::kUnknown;
      constexpr int kIntel = 0x8086;
      const auto& device = gpu_info.vulkan_info->physical_devices[0];
      switch (device.properties.vendorID) {
        case kIntel: {
          if (device.properties.driverVersion < VK_MAKE_VERSION(21, 1, 5))
            return VideoDecoderType::kUnknown;
          return VideoDecoderType::kVaapi;
        }
        default: {
          // NVIDIA drivers have a broken implementation of most va_* methods,
          // ARM & AMD aren't tested yet, and ImgTec/Qualcomm don't have a vaapi
          // driver.
          if (base::FeatureList::IsEnabled(kVaapiIgnoreDriverChecks))
            return VideoDecoderType::kVaapi;
          return VideoDecoderType::kUnknown;
        }
      }
    }
    default:
      return VideoDecoderType::kUnknown;
  }
#endif
}

}  // namespace

std::unique_ptr<VideoDecoder> CreatePlatformVideoDecoder(
    VideoDecoderTraits& traits) {
  if (traits.oop_video_decoder) {
    // TODO(b/195769334): for out-of-process video decoding, we don't need a
    // |frame_pool| because the buffers will be allocated and managed
    // out-of-process.
    auto frame_pool = std::make_unique<PlatformVideoFramePool>();

    // With out-of-process video decoding, we don't feed wrapped frames to the
    // MailboxVideoFrameConverter, so we need to pass base::NullCallback() as
    // the callback for unwrapping.
    auto frame_converter = MailboxVideoFrameConverter::Create(
        /*unwrap_frame_cb=*/base::NullCallback(), traits.gpu_task_runner,
        traits.get_command_buffer_stub_cb,
        traits.gpu_preferences.enable_unsafe_webgpu);
    return VideoDecoderPipeline::Create(
        *traits.gpu_workarounds, traits.task_runner, std::move(frame_pool),
        std::move(frame_converter), traits.media_log->Clone(),
        std::move(traits.oop_video_decoder));
  }

  switch (GetActualPlatformDecoderImplementation(traits.gpu_preferences,
                                                 traits.gpu_info)) {
    case VideoDecoderType::kVaapi:
    case VideoDecoderType::kV4L2: {
      auto frame_pool = std::make_unique<PlatformVideoFramePool>();
      auto frame_converter = MailboxVideoFrameConverter::Create(
          base::BindRepeating(&PlatformVideoFramePool::UnwrapFrame,
                              base::Unretained(frame_pool.get())),
          traits.gpu_task_runner, traits.get_command_buffer_stub_cb,
          traits.gpu_preferences.enable_unsafe_webgpu);
      return VideoDecoderPipeline::Create(
          *traits.gpu_workarounds, traits.task_runner, std::move(frame_pool),
          std::move(frame_converter), traits.media_log->Clone(),
          /*oop_video_decoder=*/{});
    }
    case VideoDecoderType::kVda: {
      return VdaVideoDecoder::Create(
          traits.task_runner, traits.gpu_task_runner, traits.media_log->Clone(),
          *traits.target_color_space, traits.gpu_preferences,
          *traits.gpu_workarounds, traits.get_command_buffer_stub_cb,
          VideoDecodeAccelerator::Config::OutputMode::ALLOCATE);
    }
    default: {
      return nullptr;
    }
  }
}

absl::optional<SupportedVideoDecoderConfigs>
GetPlatformSupportedVideoDecoderConfigs(
    gpu::GpuDriverBugWorkarounds gpu_workarounds,
    gpu::GpuPreferences gpu_preferences,
    const gpu::GPUInfo& gpu_info,
    base::OnceCallback<SupportedVideoDecoderConfigs()> get_vda_configs) {
  VideoDecoderType decoder_implementation =
      GetActualPlatformDecoderImplementation(gpu_preferences, gpu_info);
#if BUILDFLAG(IS_LINUX)
  base::UmaHistogramEnumeration("Media.VaapiLinux.SupportedVideoDecoder",
                                decoder_implementation);
#endif
  switch (decoder_implementation) {
    case VideoDecoderType::kVda:
      return std::move(get_vda_configs).Run();
    case VideoDecoderType::kVaapi:
    case VideoDecoderType::kV4L2:
      return VideoDecoderPipeline::GetSupportedConfigs(gpu_workarounds);
    default:
      return absl::nullopt;
  }
}

VideoDecoderType GetPlatformDecoderImplementationType(
    gpu::GpuDriverBugWorkarounds gpu_workarounds,
    gpu::GpuPreferences gpu_preferences,
    const gpu::GPUInfo& gpu_info) {
  // Determine the preferred decoder based purely on compile-time and run-time
  // flags. This is not intended to determine whether the selected decoder can
  // be successfully initialized or used to decode.
#if BUILDFLAG(IS_CHROMEOS)
  return GetPreferredCrosDecoderImplementation(gpu_preferences);
#else
  return GetPreferredLinuxDecoderImplementation(gpu_preferences, gpu_info);
#endif
}

std::unique_ptr<AudioDecoder> CreatePlatformAudioDecoder(
    scoped_refptr<base::SingleThreadTaskRunner> task_runner) {
  return nullptr;
}

std::unique_ptr<AudioEncoder> CreatePlatformAudioEncoder(
    scoped_refptr<base::SequencedTaskRunner> task_runner) {
  return nullptr;
}

#if !BUILDFLAG(IS_CHROMEOS)
class CdmFactory {};
#endif  // !BUILDFLAG(IS_CHROMEOS)

std::unique_ptr<CdmFactory> CreatePlatformCdmFactory(
    mojom::FrameInterfaceFactory* frame_interfaces) {
#if BUILDFLAG(IS_CHROMEOS)
  return std::make_unique<chromeos::ChromeOsCdmFactory>(frame_interfaces);
#else   // BUILDFLAG(IS_CHROMEOS)
  return nullptr;
#endif  // BUILDFLAG(IS_CHROMEOS)
}

}  // namespace media
