// Copyright (c) 2017-2021 Vivaldi Technologies AS. All rights reserved

// Defines all the command-line switches used by Vivaldi.

#include "base/vivaldi_switches.h"

namespace switches {

// All switches in alphabetical order. The switches should be documented

// To be used to disable code that interferes automatic tests.
const char kAutoTestMode[] = "auto-test-mode";
const char kDisableVivaldi[] = "disable-vivaldi";
const char kRunningVivaldi[] = "running-vivaldi";

// Enable platform media IPC demuxer that was used previously to play mpeg4
// media. Nowadays individual platform audio and video decoders should work and
// support more cases, but for testing the older code can be enabled.
const char kVivaldiEnableIPCDemuxer[] = "enable-ipc-demuxer";

// The installer should perform updates completely silently and should not
// terminate running browser instances. The name is criptic as it is not
// intended to be used by the end-user.
const char kVivaldiSilentUpdate[] = "vsu";

// Specifies a custom URL for an appcast.xml file to be used for testing
// auto updates. This switch is for internal use only and the switch name is
// pseudonymous and hard to guess due to security reasons.
const char kVivaldiUpdateURL[] = "vuu";

// Add this switch to launch the Update Notifier for component (local) builds.
#if defined(COMPONENT_BUILD)
const char kLaunchUpdater[] = "launch-updater";
#endif

// This will delay exit with a minute to be able to test the dialog that opens
// on startup if Vivaldi is already running.
const char kTestAlreadyRunningDialog[] = "test-already-running-dialog";

// Alternative language list url for translations.
const char kTranslateLanguageListUrl[] = "translate-language-list-url";

// Alternative translation server url.
const char kTranslateServerUrl[] = "translate-server-url";

}  // namespace switches
