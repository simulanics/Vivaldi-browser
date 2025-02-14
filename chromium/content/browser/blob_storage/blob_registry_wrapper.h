// Copyright 2017 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CONTENT_BROWSER_BLOB_STORAGE_BLOB_REGISTRY_WRAPPER_H_
#define CONTENT_BROWSER_BLOB_STORAGE_BLOB_REGISTRY_WRAPPER_H_

#include "base/memory/ref_counted.h"
#include "content/public/browser/browser_thread.h"
#include "third_party/blink/public/mojom/blob/blob_registry.mojom.h"

namespace storage {
class BlobRegistryImpl;
class BlobUrlRegistry;
class FileSystemContext;
}  // namespace storage

namespace content {
class ChromeBlobStorageContext;

// A ref-counted wrapper around BlobRegistryImpl to allow StoragePartitionImpl
// to own the BlobRegistry on the UI thread, while the actual BlobRegistryImpl
// lives on the IO thread.
//
// All methods, except for the constructor, are expected to be called on the IO
// thread.
class BlobRegistryWrapper
    : public base::RefCountedThreadSafe<BlobRegistryWrapper,
                                        BrowserThread::DeleteOnIOThread> {
 public:
  static scoped_refptr<BlobRegistryWrapper> Create(
      scoped_refptr<ChromeBlobStorageContext> blob_storage_context,
      scoped_refptr<storage::FileSystemContext> file_system_context,
      scoped_refptr<BlobRegistryWrapper> registry_for_fallback_url_registry =
          nullptr);

  void Bind(int process_id,
            mojo::PendingReceiver<blink::mojom::BlobRegistry> receiver);

  // TODO(mek): Make this be owned by StoragePartition directly, and living
  // on the UI thread.
  // NOTE(andre@vivaldi.com) : StoragePartition now owns url_registry_.
  storage::BlobUrlRegistry* url_registry() { return url_registry_; }

 private:
  BlobRegistryWrapper();
  friend struct BrowserThread::DeleteOnThread<BrowserThread::IO>;
  friend class base::DeleteHelper<BlobRegistryWrapper>;
  ~BlobRegistryWrapper();

  void InitializeOnIOThread(
      scoped_refptr<ChromeBlobStorageContext> blob_storage_context,
      scoped_refptr<storage::FileSystemContext> file_system_context,
      scoped_refptr<BlobRegistryWrapper> registry_for_fallback_url_registry);

  std::unique_ptr<storage::BlobRegistryImpl> blob_registry_;
  // Owned by StoragePartition.
  storage::BlobUrlRegistry* url_registry_ = nullptr;
  bool owns_bloburlregistry_ = false;
};

}  // namespace content

#endif  // CONTENT_BROWSER_BLOB_STORAGE_BLOB_REGISTRY_WRAPPER_H_
