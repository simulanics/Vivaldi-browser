// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef COMPONENTS_HISTORY_CORE_BROWSER_HISTORY_DATABASE_PARAMS_H_
#define COMPONENTS_HISTORY_CORE_BROWSER_HISTORY_DATABASE_PARAMS_H_

#include "base/files/file_path.h"
#include "components/history/core/browser/download_types.h"

namespace history {

// HistoryDatabaseParams store parameters for HistoryDatabase constructor and
// Init methods so that they can be easily passed around between HistoryService
// and HistoryBackend.
struct HistoryDatabaseParams {
  HistoryDatabaseParams();
  HistoryDatabaseParams(
      const base::FilePath& history_dir,
      DownloadInterruptReason download_interrupt_reason_none,
      DownloadInterruptReason download_interrupt_reason_crash);
  ~HistoryDatabaseParams();

  base::FilePath history_dir;
  DownloadInterruptReason download_interrupt_reason_none;
  DownloadInterruptReason download_interrupt_reason_crash;
  int number_of_days_to_keep_visits = 90;
};

}  // namespace history

#endif  // COMPONENTS_HISTORY_CORE_BROWSER_HISTORY_DATABASE_PARAMS_H_
