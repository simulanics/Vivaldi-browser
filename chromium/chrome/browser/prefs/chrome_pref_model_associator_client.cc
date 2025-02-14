// Copyright 2015 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/prefs/chrome_pref_model_associator_client.h"

#include <cstdint>

#include "base/memory/singleton.h"
#include "chrome/common/pref_names.h"
#include "components/content_settings/core/browser/website_settings_info.h"
#include "components/content_settings/core/browser/website_settings_registry.h"

#include "prefs/vivaldi_browser_prefs.h"

// static
ChromePrefModelAssociatorClient*
ChromePrefModelAssociatorClient::GetInstance() {
  return base::Singleton<ChromePrefModelAssociatorClient>::get();
}

ChromePrefModelAssociatorClient::ChromePrefModelAssociatorClient() {}

ChromePrefModelAssociatorClient::~ChromePrefModelAssociatorClient() {}

bool ChromePrefModelAssociatorClient::IsMergeableListPreference(
    const std::string& pref_name) const {
  // NOTE(igor@vivaldi.com): Do not check if Vivaldi runs to ensure that
  // a syncable list preference remains such even with --disable-vivaldi.
  if (::vivaldi::IsMergeableListPreference(pref_name))
    return true;

  return pref_name == prefs::kURLsToRestoreOnStartup;
}

bool ChromePrefModelAssociatorClient::IsMergeableDictionaryPreference(
    const std::string& pref_name) const {
  const content_settings::WebsiteSettingsRegistry& registry =
      *content_settings::WebsiteSettingsRegistry::GetInstance();
  for (const content_settings::WebsiteSettingsInfo* info : registry) {
    if (info->pref_name() == pref_name)
      return true;
  }
  return false;
}

base::Value ChromePrefModelAssociatorClient::MaybeMergePreferenceValues(
    const std::string& pref_name,
    const base::Value& local_value,
    const base::Value& server_value) const {
  if (pref_name == prefs::kNetworkEasterEggHighScore) {
    if (!local_value.is_int() || !server_value.is_int())
      return base::Value();
    return base::Value(std::max(local_value.GetInt(), server_value.GetInt()));
  }

  return base::Value();
}
