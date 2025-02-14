// Copyright (c) 2020 Vivaldi Technologies AS. All rights reserved

#include "components/datasource/vivaldi_data_url_utils.h"

#include "base/files/file.h"
#include "base/files/file_path.h"
#include "base/i18n/case_conversion.h"
#include "base/json/json_reader.h"
#include "base/logging.h"
#include "base/strings/escape.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_util.h"
#include "base/strings/utf_string_conversions.h"
#include "url/gurl.h"

#include "app/vivaldi_constants.h"
#include "components/datasource/resource_reader.h"

namespace vivaldi_data_url_utils {

const char* const kTypeNames[PathTypeCount] = {
    "local-image",       // kLocalPath
    "thumbnail",         // kImage, for historical reasons the name is not image
    "css-mods",          // kCCSMod
    "synced-store",      // kSyncedStore
    "desktop-image",     // kDesktopWallpaper
};

static_assert(sizeof(kTypeNames) / sizeof(kTypeNames[0]) ==
                  static_cast<size_t>(PathType::kLastType) + 1,
              "the name array must match the number of enum names");

namespace {

const char kOldThumbnailFormatPrefix[] = "/http://bookmark_thumbnail/";

}  // namespace

absl::optional<PathType> ParsePath(base::StringPiece path, std::string* data) {
  if (path.length() < 2 || path[0] != '/')
    return absl::nullopt;

  base::StringPiece type_piece;
  base::StringPiece data_piece;
  size_t pos = path.find('/', 1);
  if (pos != std::string::npos) {
    type_piece = path.substr(1, pos - 1);
    data_piece = path.substr(pos + 1);
  } else {
    type_piece = path.substr(1);
    data_piece = base::StringPiece();
  }

  PathType type;
  const char* const* i =
      std::find(std::cbegin(kTypeNames), std::cend(kTypeNames), type_piece);
  if (i != std::cend(kTypeNames)) {
    type = static_cast<PathType>(std::distance(std::cbegin(kTypeNames), i));
  } else {
    // Check for old-style bookmark thumbnail links where the path
    // was a full http: URL.
    base::StringPiece prefix = kOldThumbnailFormatPrefix;
    if (base::StartsWith(path, prefix)) {
      type = PathType::kImage;
      data_piece = path.substr(prefix.length());
    } else {
      return absl::nullopt;
    }
  }

  // Strip the query part from the data. We do this even when !data as we need
  // to check data_piece below.
  pos = data_piece.find('?');
  if (pos != std::string::npos) {
    data_piece = data_piece.substr(0, pos);
  }
  if (data) {
    data->assign(data_piece.data(), data_piece.length());
  }

  // Remap old /local-image/small-number path to thumbnail
  if (type == PathType::kLocalPath && isOldFormatThumbnailId(data_piece)) {
    type = PathType::kImage;
    if (data) {
      *data += ".png";
    }
  }
  return type;
}

// Parse the full url.
absl::optional<PathType> ParseUrl(base::StringPiece url, std::string* data) {
  if (url.empty())
    return absl::nullopt;

  // Short-circuit relative resource URLs to avoid the warning below as resource
  // URL is a relative URL.
  if (ResourceReader::IsResourceURL(url))
    return absl::nullopt;

  GURL gurl(url);
  if (!gurl.is_valid()) {
    LOG(WARNING) << "The url argument is not a valid URL - " << url;
    return absl::nullopt;
  }

  if (!gurl.SchemeIs(VIVALDI_DATA_URL_SCHEME))
    return absl::nullopt;

  // Treat the old format chrome://thumb/ as an alias to chrome://vivaldi-data/
  // as the path alone allows to uniquely parse it, see ParsePath().
  base::StringPiece host = gurl.host_piece();
  if (host != VIVALDI_DATA_URL_HOST && host != VIVALDI_THUMB_URL_HOST)
    return absl::nullopt;

  return ParsePath(gurl.path_piece(), data);
}

bool isOldFormatThumbnailId(base::StringPiece id) {
  int64_t bookmark_id;
  return id.length() <= 20 && base::StringToInt64(id, &bookmark_id) &&
         bookmark_id > 0;
}

bool IsBookmarkCaptureUrl(base::StringPiece url) {
  absl::optional<PathType> type = ParseUrl(url);
  return type == PathType::kImage;
}

std::string MakeUrl(PathType type, base::StringPiece data) {
  std::string url;
  url += vivaldi::kVivaldiUIDataURL;
  url += top_dir(type);
  url += '/';
  url.append(data.data(), data.length());
  return url;
}

scoped_refptr<base::RefCountedMemory> ReadFileOnBlockingThread(
    const base::FilePath& file_path,
    bool log_not_found) {
  base::File file(file_path, base::File::FLAG_READ | base::File::FLAG_OPEN);
  if (!file.IsValid()) {
    if (file.error_details() == base::File::FILE_ERROR_NOT_FOUND) {
      if (log_not_found) {
        LOG(ERROR) << "File not found - " << file_path.value();
      }
    } else {
      LOG(ERROR) << "Failed to open file " << file_path.value()
                 << " for reading";
    }
    return nullptr;
  }
  int64_t len64 = file.GetLength();
  if (len64 < 0) {
    // Realistically this can happen for named pipes or other special files.
    LOG(ERROR) << "Attempt to read from a file with no length defined "
               << file_path;
    return nullptr;
  }
  if (len64 > kMaxAllowedRead) {
    LOG(ERROR) << "File length for " << file_path << " (" << len64
               << ") exceeded " << kMaxAllowedRead;
    return nullptr;
  }
  static_assert(kMaxAllowedRead <= INT_MAX, "check that cast to int is OK");
  int len = static_cast<int>(len64);
  std::vector<unsigned char> buffer;
  if (len) {
    buffer.resize(len);
    int read_len = file.Read(0, reinterpret_cast<char*>(buffer.data()), len);
    if (read_len != len) {
      LOG(ERROR) << "Failed to read " << len << "bytes from " << file_path;
      return nullptr;
    }
  }
  return base::RefCountedBytes::TakeVector(&buffer);
}

}  // namespace vivaldi_data_url_utils