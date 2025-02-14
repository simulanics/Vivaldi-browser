// Copyright (c) 2022 Vivaldi Technologies AS. All rights reserved.

#include "sync/file_sync/file_store_impl.h"

#include <algorithm>

#include "base/containers/contains.h"
#include "base/containers/cxx20_erase_map.h"
#include "base/files/file_util.h"
#include "base/strings/string_number_conversions.h"
#include "base/task/thread_pool.h"
#include "components/base32/base32.h"
#include "crypto/sha2.h"
#include "net/base/mime_sniffer.h"

namespace file_sync {
namespace {
constexpr char kunknownFile[] = "Unknown file.";
constexpr char kMissingContent[] =
    "Placeholder for synced file. Removing this will remove the corresponding "
    "original file in the vivaldi instance that created this. Synchronization "
    "of the file content is not supported yet.";

constexpr base::FilePath::CharType kStoreDirectoryName[] =
    FILE_PATH_LITERAL("SyncedFiles");

// This wrapper ensures that we get a copy of the content itself when called
// on the thread pool, instead of a copy of a span, which wouldn't be
// thread-safe
void WriteFileWrapper(base::FilePath path, std::vector<uint8_t> content) {
  base::WriteFile(path, content);
}

std::vector<uint8_t> VectorFromString(std::string str) {
  return std::vector<uint8_t>(str.begin(), str.end());
}

}  // namespace

SyncedFileStoreImpl::SyncedFileStoreImpl(base::FilePath profile_path)
    : local_store_path_(profile_path.Append(kStoreDirectoryName)),
      file_task_runner_(base::ThreadPool::CreateSequencedTaskRunner(
          {base::MayBlock(), base::TaskPriority::USER_VISIBLE,
           base::TaskShutdownBehavior::BLOCK_SHUTDOWN})) {}

SyncedFileStoreImpl::~SyncedFileStoreImpl() = default;

void SyncedFileStoreImpl::Load() {
  SyncedFileStoreStorage::Load(
      local_store_path_, base::BindOnce(&SyncedFileStoreImpl::OnLoadingDone,
                                        weak_factory_.GetWeakPtr()));
}

void SyncedFileStoreImpl::AddOnLoadedCallback(
    base::OnceClosure on_loaded_callback) {
  DCHECK(!IsLoaded());
  on_loaded_callbacks_.push_back(std::move(on_loaded_callback));
}

void SyncedFileStoreImpl::OnLoadingDone(SyncedFilesData files_data) {
  files_data_ = std::move(files_data);

  for (auto& file_data : files_data_) {
    if (file_data.second.IsUnreferenced()) {
      DCHECK(file_data.second.has_content_locally);
      DeleteLocalContent(file_data);
    }
  }

  // Can't use a weak pointer here, because "weak_ptrs can only bind to methods
  // without return values"
  // Unretained is fine, because the callback is ultimately going to be
  // destroyed alongside with storage_
  storage_.emplace(base::BindRepeating(&SyncedFileStoreImpl::GetFilesData,
                                       base::Unretained(this)),
                   local_store_path_, file_task_runner_);

  for (const auto& file_data : files_data_) {
    for (const auto& references : file_data.second.local_references) {
      for (const auto& owner : references.second) {
        checksums_for_local_owners_[references.first][owner] = file_data.first;
      }
    }
  }

  for (const auto& file_data : files_data_) {
    for (const auto& references : file_data.second.sync_references) {
      for (const auto& owner : references.second) {
        checksums_for_sync_owners_[references.first][owner] = file_data.first;
      }
    }
  }

  for (auto& on_loaded_callback : on_loaded_callbacks_)
    std::move(on_loaded_callback).Run();

  on_loaded_callbacks_.clear();
}

void SyncedFileStoreImpl::AddLocalFileRef(base::GUID owner_guid,
                                          syncer::ModelType sync_type,
                                          std::string checksum) {
  DCHECK(IsLoaded());

  DCHECK_EQ(0U, checksums_for_local_owners_[sync_type].count(owner_guid));
  checksums_for_local_owners_[sync_type][owner_guid] = checksum;
  files_data_[checksum].local_references[sync_type].insert(owner_guid);
  storage_->ScheduleSave();
}

std::string SyncedFileStoreImpl::AddLocalFile(base::GUID owner_guid,
                                              syncer::ModelType sync_type,
                                              std::vector<uint8_t> content) {
  DCHECK(IsLoaded());
  DCHECK(!content.empty());

  auto hash = crypto::SHA256Hash(content);

  // The checskum will be used as a file name for storage on disk. We use base32
  // in order to support case-insensitive file systems.
  std::string checksum = base32::Base32Encode(
      base::StringPiece(reinterpret_cast<const char*>(hash.data()),
                        hash.size()),
      base32::Base32EncodePolicy::OMIT_PADDING);

  checksum += "." + base::NumberToString(content.size());
  DCHECK(IsLoaded());
  auto& file_data = files_data_[checksum];
  DCHECK_EQ(0U, checksums_for_local_owners_[sync_type].count(owner_guid));
  checksums_for_local_owners_[sync_type][owner_guid] = checksum;
  file_data.local_references[sync_type].insert(owner_guid);
  if (!file_data.content) {
    net::SniffMimeTypeFromLocalData(
        base::StringPiece(reinterpret_cast<char*>(&content[0]), content.size()),
        &file_data.mimetype);
    if (file_data.mimetype.empty())
      file_data.mimetype = "text/plain";
    file_data.content = std::move(content);
    if (!file_data.has_content_locally) {
      // We should only be in the process of deleting if we had local content in
      // the first place.
      DCHECK(!file_data.is_deleting);
      file_task_runner_->PostTask(
          FROM_HERE, base::BindOnce(&WriteFileWrapper, GetFilePath(checksum),
                                    *file_data.content));
      file_data.has_content_locally = true;
    }

    // Unlikely to occur but there might be pending requests for a duplicate of
    // the file we are just adding.
    file_data.RunPendingCallbacks();
  } else {
    DCHECK(file_data.content == content);
  }
  storage_->ScheduleSave();

  return checksum;
}

void SyncedFileStoreImpl::AddSyncFileRef(std::string owner_sync_id,
                                         syncer::ModelType sync_type,
                                         std::string checksum) {
  DCHECK(IsLoaded());

  DCHECK_EQ(0U, checksums_for_sync_owners_[sync_type].count(owner_sync_id));
  checksums_for_sync_owners_[sync_type][owner_sync_id] = checksum;
  files_data_[checksum].sync_references[sync_type].insert(owner_sync_id);
  storage_->ScheduleSave();
}

void SyncedFileStoreImpl::GetFile(std::string checksum,
                                  GetFileCallback callback) {
  DCHECK(IsLoaded());

  if (!base::Contains(files_data_, checksum)) {
    std::move(callback).Run(VectorFromString(kunknownFile));
    return;
  }

  auto& file_data = files_data_.at(checksum);
  if (file_data.content) {
    std::move(callback).Run(*file_data.content);
    return;
  }

  if (!file_data.has_content_locally) {
    std::move(callback).Run(VectorFromString(kMissingContent));
    return;
  }

  bool first_read_attempt = file_data.pending_callbacks.empty();
  file_data.pending_callbacks.push_back(std::move(callback));
  if (!first_read_attempt) {
    // A request for the file is already being processed. All callbacks will be
    // invoked once the content is available.
    return;
  }

  file_task_runner_->PostTaskAndReplyWithResult(
      FROM_HERE, base::BindOnce(&base::ReadFileToBytes, GetFilePath(checksum)),
      base::BindOnce(&SyncedFileStoreImpl::OnReadContentDone,
                     weak_factory_.GetWeakPtr(), checksum));
}

std::string SyncedFileStoreImpl::GetMimeType(std::string checksum) {
  DCHECK(IsLoaded());
  if (!base::Contains(files_data_, checksum))
    return "text/plain";
  std::string mimetype = files_data_.at(checksum).mimetype;
  if (mimetype.empty())
    return "text/plain";
  return files_data_.at(checksum).mimetype;
}

void SyncedFileStoreImpl::RemoveLocalRef(base::GUID owner_guid,
                                         syncer::ModelType sync_type) {
  DCHECK(IsLoaded());
  auto checksum_node =
      checksums_for_local_owners_[sync_type].extract(owner_guid);
  if (!checksum_node)
    return;

  auto file_data = files_data_.find(checksum_node.mapped());
  DCHECK(file_data != files_data_.end());
  file_data->second.local_references[sync_type].erase(owner_guid);
  if (file_data->second.IsUnreferenced()) {
    if (file_data->second.has_content_locally)
      DeleteLocalContent(*file_data);
    else
      files_data_.erase(file_data);
  }

  storage_->ScheduleSave();
}

void SyncedFileStoreImpl::RemoveSyncRef(std::string owner_sync_id,
                                        syncer::ModelType sync_type) {
  DCHECK(IsLoaded());
  auto checksum_node =
      checksums_for_sync_owners_[sync_type].extract(owner_sync_id);
  if (!checksum_node)
    return;

  auto file_data = files_data_.find(checksum_node.mapped());
  DCHECK(file_data != files_data_.end());

  file_data->second.sync_references[sync_type].erase(owner_sync_id);
  if (file_data->second.IsUnreferenced()) {
    if (file_data->second.has_content_locally)
      DeleteLocalContent(*file_data);
    else
      files_data_.erase(file_data);
  }
  storage_->ScheduleSave();
}

void SyncedFileStoreImpl::RemoveAllSyncRefsForType(
    syncer::ModelType sync_type) {
  DCHECK(IsLoaded());

  checksums_for_sync_owners_.erase(sync_type);
  base::EraseIf(files_data_, [this, sync_type](auto& file_data) {
    file_data.second.sync_references[sync_type].clear();
    if (file_data.second.IsUnreferenced()) {
      if (!file_data.second.has_content_locally)
        return true;
      DeleteLocalContent(file_data);
    }
    return false;
  });
  storage_->ScheduleSave();
}

void SyncedFileStoreImpl::OnReadContentDone(
    std::string checksum,
    absl::optional<std::vector<uint8_t>> content) {
  if (!base::Contains(files_data_, checksum)) {
    // The file was removed in the interval since the read was required.
    return;
  }

  if (!content) {
    return;
  }

  auto& file_data = files_data_.at(checksum);
  DCHECK(file_data.has_content_locally);
  if (file_data.content) {
    // The content was obtained from a different source in the interval.
    DCHECK(file_data.content == content);
    return;
  }

  file_data.content = std::move(content);
  file_data.RunPendingCallbacks();
}

bool SyncedFileStoreImpl::IsLoaded() {
  // We instanciate storage only after loading is done
  return storage_.has_value();
}

base::FilePath SyncedFileStoreImpl::GetFilePath(
    const std::string& checksum) const {
  return local_store_path_.Append(base::FilePath::FromASCII(checksum));
}

const SyncedFilesData& SyncedFileStoreImpl::GetFilesData() {
  DCHECK(IsLoaded());
  return files_data_;
}

void SyncedFileStoreImpl::DeleteLocalContent(
    std::pair<const std::string, SyncedFileData>& file_data) {
  DCHECK(file_data.second.has_content_locally);
  // Avoid triggering multiple deletions
  file_data.second.content = absl::nullopt;
  if (file_data.second.is_deleting)
    return;

  file_data.second.is_deleting = true;
  file_task_runner_->PostTask(
      FROM_HERE,
      base::GetDeleteFileCallback(
          GetFilePath(file_data.first),
          base::BindOnce(&SyncedFileStoreImpl::OnLocalContentDeleted,
                         weak_factory_.GetWeakPtr(), file_data.first)));
}

void SyncedFileStoreImpl::OnLocalContentDeleted(const std::string& checksum,
                                                bool success) {
  auto file_data = files_data_.find(checksum);

  // The metadata shouldn't be removed before the content is gone.
  DCHECK(file_data != files_data_.end());

  // If we didn't succeed, we'll try again next time the store is loaded.
  if (!success) {
    file_data->second.is_deleting = false;
    return;
  }

  // The file may have been re-added while deletion was taking place.
  if (file_data->second.IsUnreferenced()) {
    files_data_.erase(file_data);
  } else if (file_data->second.content) {
    // The file was re-added and we have its content. Recreate the file on disk.
    file_task_runner_->PostTask(
        FROM_HERE, base::BindOnce(&WriteFileWrapper, GetFilePath(checksum),
                                  *file_data->second.content));
  }

  storage_->ScheduleSave();
}
}  // namespace file_sync