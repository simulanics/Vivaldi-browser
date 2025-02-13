// Copyright 2017 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "components/sync_bookmarks/bookmark_model_type_processor.h"

#include <utility>

#include "base/bind.h"
#include "base/callback.h"
#include "base/feature_list.h"
#include "base/logging.h"
#include "base/memory/raw_ptr.h"
#include "base/strings/utf_string_conversions.h"
#include "base/threading/sequenced_task_runner_handle.h"
#include "base/trace_event/memory_usage_estimator.h"
#include "base/trace_event/trace_event.h"
#include "components/bookmarks/browser/bookmark_model.h"
#include "components/bookmarks/browser/bookmark_node.h"
#include "components/bookmarks/browser/bookmark_utils.h"
#include "components/sync/base/client_tag_hash.h"
#include "components/sync/base/data_type_histogram.h"
#include "components/sync/base/model_type.h"
#include "components/sync/base/time.h"
#include "components/sync/engine/commit_queue.h"
#include "components/sync/engine/data_type_activation_response.h"
#include "components/sync/engine/model_type_processor_metrics.h"
#include "components/sync/engine/model_type_processor_proxy.h"
#include "components/sync/model/data_type_activation_request.h"
#include "components/sync/model/type_entities_count.h"
#include "components/sync/protocol/bookmark_model_metadata.pb.h"
#include "components/sync/protocol/proto_value_conversions.h"
#include "components/sync_bookmarks/bookmark_local_changes_builder.h"
#include "components/sync_bookmarks/bookmark_model_merger.h"
#include "components/sync_bookmarks/bookmark_model_observer_impl.h"
#include "components/sync_bookmarks/bookmark_remote_updates_handler.h"
#include "components/sync_bookmarks/bookmark_specifics_conversions.h"
#include "components/sync_bookmarks/parent_guid_preprocessing.h"
#include "components/sync_bookmarks/switches.h"
#include "components/sync_bookmarks/synced_bookmark_tracker_entity.h"
#include "components/undo/bookmark_undo_utils.h"

#include "app/vivaldi_apptools.h"

namespace sync_bookmarks {

namespace {

class ScopedRemoteUpdateBookmarks {
 public:
  // |bookmark_model|, |bookmark_undo_service| and |observer| must not be null
  // and must outlive this object.
  ScopedRemoteUpdateBookmarks(bookmarks::BookmarkModel* bookmark_model,
                              BookmarkUndoService* bookmark_undo_service,
                              bookmarks::BookmarkModelObserver* observer)
      : bookmark_model_(bookmark_model),
        suspend_undo_(bookmark_undo_service),
        observer_(observer) {
    // Notify UI intensive observers of BookmarkModel that we are about to make
    // potentially significant changes to it, so the updates may be batched. For
    // example, on Mac, the bookmarks bar displays animations when bookmark
    // items are added or deleted.
    DCHECK(bookmark_model_);
    bookmark_model_->BeginExtensiveChanges();
    // Shouldn't be notified upon changes due to sync.
    bookmark_model_->RemoveObserver(observer_);
  }

  ScopedRemoteUpdateBookmarks(const ScopedRemoteUpdateBookmarks&) = delete;
  ScopedRemoteUpdateBookmarks& operator=(const ScopedRemoteUpdateBookmarks&) =
      delete;

  ~ScopedRemoteUpdateBookmarks() {
    // Notify UI intensive observers of BookmarkModel that all updates have been
    // applied, and that they may now be consumed. This prevents issues like the
    // one described in https://crbug.com/281562, where old and new items on the
    // bookmarks bar would overlap.
    bookmark_model_->EndExtensiveChanges();
    bookmark_model_->AddObserver(observer_);
  }

 private:
  const raw_ptr<bookmarks::BookmarkModel> bookmark_model_;

  // Changes made to the bookmark model due to sync should not be undoable.
  ScopedSuspendBookmarkUndo suspend_undo_;

  const raw_ptr<bookmarks::BookmarkModelObserver> observer_;
};

std::string ComputeServerDefinedUniqueTagForDebugging(
    const bookmarks::BookmarkNode* node,
    bookmarks::BookmarkModel* model) {
  if (node == model->bookmark_bar_node()) {
    return "bookmark_bar";
  }
  if (node == model->other_node()) {
    return "other_bookmarks";
  }
  if (node == model->mobile_node()) {
    return "synced_bookmarks";
  }
  if (node == model->trash_node()) {
    return "trash_bookmarks";
  }
  return "";
}

}  // namespace

BookmarkModelTypeProcessor::BookmarkModelTypeProcessor(
    BookmarkUndoService* bookmark_undo_service)
    : bookmark_undo_service_(bookmark_undo_service) {}

BookmarkModelTypeProcessor::~BookmarkModelTypeProcessor() {
  if (bookmark_model_ && bookmark_model_observer_) {
    bookmark_model_->RemoveObserver(bookmark_model_observer_.get());
  }
}

void BookmarkModelTypeProcessor::ConnectSync(
    std::unique_ptr<syncer::CommitQueue> worker) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  DCHECK(!worker_);
  DCHECK(bookmark_model_);

  worker_ = std::move(worker);

  // |bookmark_tracker_| is instantiated only after initial sync is done.
  if (bookmark_tracker_) {
    NudgeForCommitIfNeeded();
  }
}

void BookmarkModelTypeProcessor::DisconnectSync() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);

  weak_ptr_factory_for_worker_.InvalidateWeakPtrs();
  if (!worker_) {
    return;
  }

  DVLOG(1) << "Disconnecting sync for Bookmarks";
  worker_.reset();
}

void BookmarkModelTypeProcessor::GetLocalChanges(
    size_t max_entries,
    GetLocalChangesCallback callback) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  BookmarkLocalChangesBuilder builder(bookmark_tracker_.get(), bookmark_model_);
  std::move(callback).Run(builder.BuildCommitRequests(max_entries));
}

void BookmarkModelTypeProcessor::OnCommitCompleted(
    const sync_pb::ModelTypeState& type_state,
    const syncer::CommitResponseDataList& committed_response_list,
    const syncer::FailedCommitResponseDataList& error_response_list) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);

  // |error_response_list| is ignored, because all errors are treated as
  // transient and the processor with eventually retry.
  for (const syncer::CommitResponseData& response : committed_response_list) {
    const SyncedBookmarkTrackerEntity* entity =
        bookmark_tracker_->GetEntityForClientTagHash(response.client_tag_hash);
    if (!entity) {
      DLOG(WARNING) << "Received a commit response for an unknown entity.";
      continue;
    }

    bookmark_tracker_->UpdateUponCommitResponse(entity, response.id,
                                                response.response_version,
                                                response.sequence_number);
  }

  bookmark_tracker_->set_model_type_state(type_state);
  schedule_save_closure_.Run();
}

void BookmarkModelTypeProcessor::OnUpdateReceived(
    const sync_pb::ModelTypeState& model_type_state,
    syncer::UpdateResponseDataList updates,
    absl::optional<sync_pb::GarbageCollectionDirective> gc_directive) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  DCHECK(!model_type_state.cache_guid().empty());
  DCHECK_EQ(model_type_state.cache_guid(), cache_guid_);
  DCHECK(model_type_state.initial_sync_done());

  // TODO(crbug.com/1356900): validate incoming updates, e.g. |gc_directive|
  // must be empty for Bookmarks.

  syncer::LogUpdatesReceivedByProcessorHistogram(
      syncer::BOOKMARKS,
      /*is_initial_sync=*/!bookmark_tracker_, updates.size());

  // Clients before M94 did not populate the parent GUID in specifics.
  PopulateParentGuidInSpecifics(bookmark_tracker_.get(), &updates);

  if (!bookmark_tracker_) {
    OnInitialUpdateReceived(model_type_state, std::move(updates));
    return;
  }

  // Incremental updates.
  ScopedRemoteUpdateBookmarks update_bookmarks(
      bookmark_model_, bookmark_undo_service_, bookmark_model_observer_.get());
  BookmarkRemoteUpdatesHandler updates_handler(
      bookmark_model_, favicon_service_, bookmark_tracker_.get());
  const bool got_new_encryption_requirements =
      bookmark_tracker_->model_type_state().encryption_key_name() !=
      model_type_state.encryption_key_name();
  bookmark_tracker_->set_model_type_state(model_type_state);
  updates_handler.Process(updates, got_new_encryption_requirements);
  if (bookmark_tracker_->ReuploadBookmarksOnLoadIfNeeded()) {
    NudgeForCommitIfNeeded();
  }
  // There are cases when we receive non-empty updates that don't result in
  // model changes (e.g. reflections). In that case, issue a write to persit the
  // progress marker in order to avoid downloading those updates again.
  if (!updates.empty()) {
    // Schedule save just in case one is needed.
    schedule_save_closure_.Run();
  }
}

const SyncedBookmarkTracker* BookmarkModelTypeProcessor::GetTrackerForTest()
    const {
  return bookmark_tracker_.get();
}

bool BookmarkModelTypeProcessor::IsConnectedForTest() const {
  return worker_ != nullptr;
}

std::string BookmarkModelTypeProcessor::EncodeSyncMetadata() const {
  std::string metadata_str;
  if (bookmark_tracker_) {
    sync_pb::BookmarkModelMetadata model_metadata =
        bookmark_tracker_->BuildBookmarkModelMetadata();
    model_metadata.SerializeToString(&metadata_str);
  }
  return metadata_str;
}

void BookmarkModelTypeProcessor::ModelReadyToSync(
    const std::string& metadata_str,
    const base::RepeatingClosure& schedule_save_closure,
    bookmarks::BookmarkModel* model) {
  DCHECK(model);
  DCHECK(model->loaded());
  DCHECK(!bookmark_model_);
  DCHECK(!bookmark_tracker_);
  DCHECK(!bookmark_model_observer_);

  // TODO(crbug.com/950869): Remove after investigations are completed.
  TRACE_EVENT0("browser", "BookmarkModelTypeProcessor::ModelReadyToSync");

  bookmark_model_ = model;
  schedule_save_closure_ = schedule_save_closure;

  sync_pb::BookmarkModelMetadata model_metadata;
  model_metadata.ParseFromString(metadata_str);

  bookmark_tracker_ = SyncedBookmarkTracker::CreateFromBookmarkModelAndMetadata(
      model, std::move(model_metadata));

  if (bookmark_tracker_) {
    StartTrackingMetadata();
  } else if (!metadata_str.empty()) {
    DLOG(WARNING)
        << "Persisted bookmark sync metadata invalidated when loading.";
    // Schedule a save to make sure the corrupt metadata is deleted from disk as
    // soon as possible, to avoid reporting again after restart if nothing else
    // schedules a save meanwhile (which is common if sync is not running
    // properly, e.g. auth error).
    schedule_save_closure_.Run();
  }

  ConnectIfReady();
}

void BookmarkModelTypeProcessor::SetFaviconService(
    favicon::FaviconService* favicon_service) {
  DCHECK(favicon_service);
  favicon_service_ = favicon_service;
}

size_t BookmarkModelTypeProcessor::EstimateMemoryUsage() const {
  using base::trace_event::EstimateMemoryUsage;
  size_t memory_usage = 0;
  if (bookmark_tracker_) {
    memory_usage += bookmark_tracker_->EstimateMemoryUsage();
  }
  memory_usage += EstimateMemoryUsage(cache_guid_);
  return memory_usage;
}

base::WeakPtr<syncer::ModelTypeControllerDelegate>
BookmarkModelTypeProcessor::GetWeakPtr() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  return weak_ptr_factory_for_controller_.GetWeakPtr();
}

void BookmarkModelTypeProcessor::OnSyncStarting(
    const syncer::DataTypeActivationRequest& request,
    StartCallback start_callback) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  DCHECK(start_callback);
  // |favicon_service_| should have been set by now.
  DCHECK(favicon_service_);
  DVLOG(1) << "Sync is starting for Bookmarks";

  cache_guid_ = request.cache_guid;
  start_callback_ = std::move(start_callback);
  error_handler_ = request.error_handler;

  DCHECK(!cache_guid_.empty());
  ConnectIfReady();
}

void BookmarkModelTypeProcessor::ConnectIfReady() {
  // Return if the model isn't ready.
  if (!bookmark_model_) {
    return;
  }
  // Return if Sync didn't start yet.
  if (!start_callback_) {
    return;
  }

  DCHECK(!cache_guid_.empty());

  if (bookmark_tracker_ &&
      bookmark_tracker_->model_type_state().cache_guid() != cache_guid_) {
    // TODO(crbug.com/820049): Add basic unit testing  consider using
    // StopTrackingMetadata().
    // In case of a cache guid mismatch, treat it as a corrupted metadata and
    // start clean.
    bookmark_model_->RemoveObserver(bookmark_model_observer_.get());
    bookmark_model_observer_.reset();
    bookmark_tracker_.reset();
  }

  auto activation_context =
      std::make_unique<syncer::DataTypeActivationResponse>();
  if (bookmark_tracker_) {
    activation_context->model_type_state =
        bookmark_tracker_->model_type_state();
  } else {
    sync_pb::ModelTypeState model_type_state;
    model_type_state.mutable_progress_marker()->set_data_type_id(
        GetSpecificsFieldNumberFromModelType(syncer::BOOKMARKS));
    model_type_state.set_cache_guid(cache_guid_);
    activation_context->model_type_state = model_type_state;
  }
  activation_context->type_processor =
      std::make_unique<syncer::ModelTypeProcessorProxy>(
          weak_ptr_factory_for_worker_.GetWeakPtr(),
          base::SequencedTaskRunnerHandle::Get());
  std::move(start_callback_).Run(std::move(activation_context));
}

void BookmarkModelTypeProcessor::OnSyncStopping(
    syncer::SyncStopMetadataFate metadata_fate) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  // Disabling sync for a type shouldn't happen before the model is loaded
  // because OnSyncStopping() is not allowed to be called before
  // OnSyncStarting() has completed..
  DCHECK(bookmark_model_);
  DCHECK(!start_callback_);

  cache_guid_.clear();
  worker_.reset();

  switch (metadata_fate) {
    case syncer::KEEP_METADATA: {
      break;
    }

    case syncer::CLEAR_METADATA: {
      // Stop observing local changes. We'll start observing local changes again
      // when Sync is (re)started in StartTrackingMetadata().
      if (bookmark_tracker_) {
        DCHECK(bookmark_model_observer_);
        bookmark_model_->RemoveObserver(bookmark_model_observer_.get());
        bookmark_model_observer_.reset();
        bookmark_tracker_.reset();
      }
      schedule_save_closure_.Run();
      break;
    }
  }

  // Do not let any delayed callbacks to be called.
  weak_ptr_factory_for_controller_.InvalidateWeakPtrs();
  weak_ptr_factory_for_worker_.InvalidateWeakPtrs();
}

void BookmarkModelTypeProcessor::NudgeForCommitIfNeeded() {
  DCHECK(bookmark_tracker_);
  // Don't bother sending anything if there's no one to send to.
  if (!worker_) {
    return;
  }

  // Nudge worker if there are any entities with local changes.
  if (bookmark_tracker_->HasLocalChanges()) {
    worker_->NudgeForCommit();
  }
}

void BookmarkModelTypeProcessor::OnBookmarkModelBeingDeleted() {
  DCHECK(bookmark_model_);
  DCHECK(bookmark_model_observer_);
  StopTrackingMetadata();
}

void BookmarkModelTypeProcessor::OnInitialUpdateReceived(
    const sync_pb::ModelTypeState& model_type_state,
    syncer::UpdateResponseDataList updates) {
  DCHECK(!bookmark_tracker_);

  TRACE_EVENT0("sync", "BookmarkModelTypeProcessor::OnInitialUpdateReceived");

  bookmark_tracker_ = SyncedBookmarkTracker::CreateEmpty(model_type_state);
  StartTrackingMetadata();

  {
    ScopedRemoteUpdateBookmarks update_bookmarks(
        bookmark_model_, bookmark_undo_service_,
        bookmark_model_observer_.get());

    BookmarkModelMerger model_merger(std::move(updates), bookmark_model_,
                                     favicon_service_, bookmark_tracker_.get());
    model_merger.Merge();
  }

  // If any of the permanent nodes is missing, we treat it as failure.
  if (!bookmark_tracker_->GetEntityForBookmarkNode(
          bookmark_model_->bookmark_bar_node()) ||
      !bookmark_tracker_->GetEntityForBookmarkNode(
          bookmark_model_->other_node()) ||
        (vivaldi::IsVivaldiRunning() &&
            !bookmark_tracker_->GetEntityForBookmarkNode(
                bookmark_model_->trash_node())) ||
      !bookmark_tracker_->GetEntityForBookmarkNode(
          bookmark_model_->mobile_node())) {
    StopTrackingMetadata();
    bookmark_tracker_.reset();
    error_handler_.Run(
        syncer::ModelError(FROM_HERE, "Permanent bookmark entities missing"));
    return;
  }

  bookmark_tracker_->CheckAllNodesTracked(bookmark_model_);

  schedule_save_closure_.Run();
  NudgeForCommitIfNeeded();
}

void BookmarkModelTypeProcessor::StartTrackingMetadata() {
  DCHECK(bookmark_tracker_);
  DCHECK(!bookmark_model_observer_);

  bookmark_model_observer_ = std::make_unique<BookmarkModelObserverImpl>(
      base::BindRepeating(&BookmarkModelTypeProcessor::NudgeForCommitIfNeeded,
                          base::Unretained(this)),
      base::BindOnce(&BookmarkModelTypeProcessor::OnBookmarkModelBeingDeleted,
                     base::Unretained(this)),
      bookmark_tracker_.get());
  bookmark_model_->AddObserver(bookmark_model_observer_.get());
}

void BookmarkModelTypeProcessor::StopTrackingMetadata() {
  DCHECK(bookmark_model_observer_);

  bookmark_model_->RemoveObserver(bookmark_model_observer_.get());
  bookmark_model_ = nullptr;
  bookmark_model_observer_.reset();

  DisconnectSync();
}

void BookmarkModelTypeProcessor::GetAllNodesForDebugging(
    AllNodesCallback callback) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  base::Value::List all_nodes;
  // Create a permanent folder since sync server no longer create root folders,
  // and USS won't migrate root folders from directory, we create root folders.
  base::Value::Dict root_node;
  // Function isTypeRootNode in sync_node_browser.js use PARENT_ID and
  // UNIQUE_SERVER_TAG to check if the node is root node. isChildOf in
  // sync_node_browser.js uses modelType to check if root node is parent of real
  // data node. NON_UNIQUE_NAME will be the name of node to display.
  root_node.Set("ID", "BOOKMARKS_ROOT");
  root_node.Set("PARENT_ID", "r");
  root_node.Set("UNIQUE_SERVER_TAG", "Bookmarks");
  root_node.Set("IS_DIR", true);
  root_node.Set("modelType", "Bookmarks");
  root_node.Set("NON_UNIQUE_NAME", "Bookmarks");
  all_nodes.Append(std::move(root_node));

  const bookmarks::BookmarkNode* model_root_node = bookmark_model_->root_node();
  int i = 0;
  for (const auto& child : model_root_node->children()) {
    AppendNodeAndChildrenForDebugging(child.get(), i++, &all_nodes);
  }

  std::move(callback).Run(syncer::BOOKMARKS, std::move(all_nodes));
}

void BookmarkModelTypeProcessor::AppendNodeAndChildrenForDebugging(
    const bookmarks::BookmarkNode* node,
    int index,
    base::Value::List* all_nodes) const {
  const SyncedBookmarkTrackerEntity* entity =
      bookmark_tracker_->GetEntityForBookmarkNode(node);
  // Include only tracked nodes. Newly added nodes are tracked even before being
  // sent to the server. Managed bookmarks (that are installed by a policy)
  // aren't syncable and hence not tracked.
  if (!entity) {
    return;
  }
  const sync_pb::EntityMetadata& metadata = entity->metadata();
  // Copy data to an EntityData object to reuse its conversion
  // ToDictionaryValue() methods.
  syncer::EntityData data;
  data.id = metadata.server_id();
  data.creation_time = node->date_added();
  data.modification_time =
      syncer::ProtoTimeToTime(metadata.modification_time());
  data.name = base::UTF16ToUTF8(node->GetTitle());
  data.specifics = CreateSpecificsFromBookmarkNode(
      node, bookmark_model_, metadata.unique_position(),
      /*force_favicon_load=*/false);

  if (node->is_permanent_node()) {
    data.server_defined_unique_tag =
        ComputeServerDefinedUniqueTagForDebugging(node, bookmark_model_);
    // Set the parent to empty string to indicate it's parent of the root node
    // for bookmarks. The code in sync_node_browser.js links nodes with the
    // "modelType" when they are lacking a parent id.
    data.legacy_parent_id = "";
  } else {
    const bookmarks::BookmarkNode* parent = node->parent();
    const SyncedBookmarkTrackerEntity* parent_entity =
        bookmark_tracker_->GetEntityForBookmarkNode(parent);
    DCHECK(parent_entity);
    data.legacy_parent_id = parent_entity->metadata().server_id();
  }

  base::Value::Dict data_dictionary = data.ToDictionaryValue();
  // Set ID value as in legacy directory-based implementation, "s" means server.
  data_dictionary.Set("ID", "s" + metadata.server_id());
  if (node->is_permanent_node()) {
    // Hardcode the parent of permanent nodes.
    data_dictionary.Set("PARENT_ID", "BOOKMARKS_ROOT");
    data_dictionary.Set("UNIQUE_SERVER_TAG", data.server_defined_unique_tag);
  } else {
    data_dictionary.Set("PARENT_ID", "s" + data.legacy_parent_id);
  }
  data_dictionary.Set("LOCAL_EXTERNAL_ID", static_cast<int>(node->id()));
  data_dictionary.Set("positionIndex", index);
  data_dictionary.Set("metadata", base::Value::FromUniquePtrValue(
                                      syncer::EntityMetadataToValue(metadata)));
  data_dictionary.Set("modelType", "Bookmarks");
  data_dictionary.Set("IS_DIR", node->is_folder());
  all_nodes->Append(std::move(data_dictionary));

  int i = 0;
  for (const auto& child : node->children()) {
    AppendNodeAndChildrenForDebugging(child.get(), i++, all_nodes);
  }
}

void BookmarkModelTypeProcessor::GetTypeEntitiesCountForDebugging(
    base::OnceCallback<void(const syncer::TypeEntitiesCount&)> callback) const {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  syncer::TypeEntitiesCount count(syncer::BOOKMARKS);
  if (bookmark_tracker_) {
    count.non_tombstone_entities = bookmark_tracker_->TrackedBookmarksCount();
    count.entities = count.non_tombstone_entities +
                     bookmark_tracker_->TrackedUncommittedTombstonesCount();
  }
  std::move(callback).Run(count);
}

void BookmarkModelTypeProcessor::RecordMemoryUsageAndCountsHistograms() {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  SyncRecordModelTypeMemoryHistogram(syncer::BOOKMARKS, EstimateMemoryUsage());
  if (bookmark_tracker_) {
    SyncRecordModelTypeCountHistogram(
        syncer::BOOKMARKS, bookmark_tracker_->TrackedBookmarksCount());
  } else {
    SyncRecordModelTypeCountHistogram(syncer::BOOKMARKS, 0);
  }
}

}  // namespace sync_bookmarks
