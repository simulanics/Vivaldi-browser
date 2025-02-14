// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "third_party/blink/renderer/core/layout/ng/ng_out_of_flow_layout_part.h"

#include <math.h>

#include "third_party/blink/renderer/core/layout/deferred_shaping.h"
#include "third_party/blink/renderer/core/layout/layout_block.h"
#include "third_party/blink/renderer/core/layout/layout_box.h"
#include "third_party/blink/renderer/core/layout/layout_flexible_box.h"
#include "third_party/blink/renderer/core/layout/layout_inline.h"
#include "third_party/blink/renderer/core/layout/layout_object.h"
#include "third_party/blink/renderer/core/layout/ng/grid/ng_grid_layout_algorithm.h"
#include "third_party/blink/renderer/core/layout/ng/grid/ng_grid_placement.h"
#include "third_party/blink/renderer/core/layout/ng/inline/ng_physical_line_box_fragment.h"
#include "third_party/blink/renderer/core/layout/ng/layout_box_utils.h"
#include "third_party/blink/renderer/core/layout/ng/layout_ng_view.h"
#include "third_party/blink/renderer/core/layout/ng/legacy_layout_tree_walking.h"
#include "third_party/blink/renderer/core/layout/ng/ng_absolute_utils.h"
#include "third_party/blink/renderer/core/layout/ng/ng_constraint_space_builder.h"
#include "third_party/blink/renderer/core/layout/ng/ng_disable_side_effects_scope.h"
#include "third_party/blink/renderer/core/layout/ng/ng_fragment.h"
#include "third_party/blink/renderer/core/layout/ng/ng_layout_result.h"
#include "third_party/blink/renderer/core/layout/ng/ng_out_of_flow_positioned_node.h"
#include "third_party/blink/renderer/core/layout/ng/ng_physical_box_fragment.h"
#include "third_party/blink/renderer/core/layout/ng/ng_physical_fragment.h"
#include "third_party/blink/renderer/core/layout/ng/ng_simplified_layout_algorithm.h"
#include "third_party/blink/renderer/core/layout/ng/ng_simplified_oof_layout_algorithm.h"
#include "third_party/blink/renderer/core/paint/paint_layer.h"
#include "third_party/blink/renderer/core/paint/paint_layer_scrollable_area.h"
#include "third_party/blink/renderer/core/style/computed_style.h"
#include "third_party/blink/renderer/platform/heap/collection_support/clear_collection_scope.h"

namespace blink {

namespace {

bool IsInPreOrder(const HeapVector<NGLogicalOOFNodeForFragmentation>& nodes) {
  return std::is_sorted(nodes.begin(), nodes.end(),
                        [](const NGLogicalOOFNodeForFragmentation& a,
                           const NGLogicalOOFNodeForFragmentation& b) {
                          return a.box->IsBeforeInPreOrder(*b.box);
                        });
}

void SortInPreOrder(HeapVector<NGLogicalOOFNodeForFragmentation>* nodes) {
  std::sort(nodes->begin(), nodes->end(),
            [](const NGLogicalOOFNodeForFragmentation& a,
               const NGLogicalOOFNodeForFragmentation& b) {
              return a.box->IsBeforeInPreOrder(*b.box);
            });
}

}  // namespace

// static
absl::optional<LogicalSize>
NGOutOfFlowLayoutPart::InitialContainingBlockFixedSize(NGBlockNode container) {
  if (!container.GetLayoutBox()->IsLayoutView() ||
      container.GetDocument().Printing())
    return absl::nullopt;
  const auto* frame_view = container.GetDocument().View();
  DCHECK(frame_view);
  PhysicalSize size(
      frame_view->LayoutViewport()->ExcludeScrollbars(frame_view->Size()));
  return size.ConvertToLogical(container.Style().GetWritingMode());
}

NGOutOfFlowLayoutPart::NGOutOfFlowLayoutPart(
    const NGBlockNode& container_node,
    const NGConstraintSpace& container_space,
    NGBoxFragmentBuilder* container_builder)
    : NGOutOfFlowLayoutPart(container_node.IsAbsoluteContainer(),
                            container_node.IsFixedContainer(),
                            container_node.IsGrid(),
                            container_space,
                            container_builder,
                            InitialContainingBlockFixedSize(container_node)) {}

NGOutOfFlowLayoutPart::NGOutOfFlowLayoutPart(
    bool is_absolute_container,
    bool is_fixed_container,
    bool is_grid_container,
    const NGConstraintSpace& container_space,
    NGBoxFragmentBuilder* container_builder,
    absl::optional<LogicalSize> initial_containing_block_fixed_size)
    : container_builder_(container_builder),
      is_absolute_container_(is_absolute_container),
      is_fixed_container_(is_fixed_container),
      has_block_fragmentation_(container_space.HasBlockFragmentation()) {
  // TODO(almaher): Should we early return here in the case of block
  // fragmentation? If not, what should |allow_first_tier_oof_cache_| be set to
  // in this case?
  if (!container_builder->HasOutOfFlowPositionedCandidates() &&
      !container_builder->HasOutOfFlowFragmentainerDescendants() &&
      !container_builder->HasMulticolsWithPendingOOFs() &&
      !To<LayoutBlock>(container_builder_->GetLayoutObject())
           ->HasPositionedObjects())
    return;

  // Disable first tier cache for grid layouts, as grid allows for out-of-flow
  // items to be placed in grid areas, which is complex to maintain a cache for.
  const NGBoxStrut border_scrollbar =
      container_builder->Borders() + container_builder->Scrollbar();
  allow_first_tier_oof_cache_ = border_scrollbar.IsEmpty() &&
                                !is_grid_container && !has_block_fragmentation_;
  default_containing_block_info_for_absolute_.writing_direction =
      ConstraintSpace().GetWritingDirection();
  default_containing_block_info_for_fixed_.writing_direction =
      ConstraintSpace().GetWritingDirection();
  if (container_builder_->HasBlockSize()) {
    default_containing_block_info_for_absolute_.rect.size =
        ShrinkLogicalSize(container_builder_->Size(), border_scrollbar);
    default_containing_block_info_for_fixed_.rect.size =
        initial_containing_block_fixed_size
            ? *initial_containing_block_fixed_size
            : default_containing_block_info_for_absolute_.rect.size;
  }
  LogicalOffset container_offset = {border_scrollbar.inline_start,
                                    border_scrollbar.block_start};
  default_containing_block_info_for_absolute_.rect.offset = container_offset;
  default_containing_block_info_for_fixed_.rect.offset = container_offset;
}

void NGOutOfFlowLayoutPart::Run(const LayoutBox* only_layout) {
  HandleFragmentation();
  const LayoutObject* current_container = container_builder_->GetLayoutObject();
  if (!container_builder_->HasOutOfFlowPositionedCandidates() &&
      !To<LayoutBlock>(current_container)->HasPositionedObjects()) {
    container_builder_
        ->AdjustFixedposContainingBlockForFragmentainerDescendants();
    container_builder_->AdjustFixedposContainingBlockForInnerMulticols();
    return;
  }

  // If the container is display-locked, then we skip the layout of descendants,
  // so we can early out immediately.
  if (current_container->ChildLayoutBlockedByDisplayLock())
    return;

  HeapVector<NGLogicalOutOfFlowPositionedNode> candidates;
  ClearCollectionScope<HeapVector<NGLogicalOutOfFlowPositionedNode>>
      clear_scope(&candidates);
  container_builder_->SwapOutOfFlowPositionedCandidates(&candidates);

  HeapHashSet<Member<const LayoutObject>> placed_objects;
  LayoutCandidates(&candidates, only_layout, &placed_objects);

  if (only_layout)
    return;

  // If we're in a block fragmentation context (or establishing one being a
  // paginated root), we've already ruled out the possibility of having legacy
  // objects in here. The code below would pick up every OOF candidate not in
  // placed_objects, and treat them as a legacy object (even if they aren't
  // one), while in fact it could be an NG object that we have finished laying
  // out in an earlier fragmentainer. Just bail.
  if (has_block_fragmentation_ || container_builder_->Node().IsPaginatedRoot())
    return;

  wtf_size_t prev_placed_objects_size = placed_objects.size();
  bool did_get_same_object_count_once = false;
  while (SweepLegacyCandidates(&placed_objects)) {
    container_builder_->SwapOutOfFlowPositionedCandidates(&candidates);

    // We must have at least one new candidate, otherwise we shouldn't have
    // entered this branch.
    DCHECK_GT(candidates.size(), 0u);

    LayoutCandidates(&candidates, only_layout, &placed_objects);

    // Legacy currently has a bug where an OOF-positioned node is present
    // within the current node's |LayoutBlock::PositionedObjects|, however it
    // is not the containing-block for this node.
    //
    // This results in |LayoutDescendantCandidates| never performing layout on
    // any additional objects.
    wtf_size_t placed_objects_size = placed_objects.size();
    if (prev_placed_objects_size == placed_objects_size) {
      if (did_get_same_object_count_once || !has_legacy_flex_box_) {
        NOTREACHED();
        break;
      }
      // If we have an OOF legacy flex container with an (uncontained;
      // e.g. fixed inside absolute positioned) OOF flex item inside, we'll
      // allow one additional iteration, even if the object count is the
      // same. In the first iteration the objects in
      // LayoutBlock::PositionedObjects() were not in document order, and then
      // corrected afterwards (before we get here). Only allow this to happen
      // once, to avoid infinite loops for whatever reason, and good fortune.
      did_get_same_object_count_once = true;
    }
    prev_placed_objects_size = placed_objects_size;
  }
}

// Gather candidates that weren't present in the OOF candidates list.
// This occurs when a candidate is separated from container by a legacy node.
// E.g.
// <div style="position: relative;">
//   <div style="display: flex;">
//     <div style="position: absolute;"></div>
//   </div>
// </div>
// Returns false if no new candidates were found.
bool NGOutOfFlowLayoutPart::SweepLegacyCandidates(
    HeapHashSet<Member<const LayoutObject>>* placed_objects) {
  const auto* container_block =
      DynamicTo<LayoutBlock>(container_builder_->GetLayoutObject());
  if (!container_block)
    return false;
  TrackedLayoutBoxLinkedHashSet* legacy_objects =
      container_block->PositionedObjects();

  bool are_legacy_objects_already_placed = true;
  if (legacy_objects) {
    for (LayoutObject* legacy_object : *legacy_objects) {
      if (!placed_objects->Contains(legacy_object)) {
        are_legacy_objects_already_placed = false;
        break;
      }
    }
  }

  if (!legacy_objects || are_legacy_objects_already_placed) {
    if (!has_legacy_flex_box_ || performing_extra_legacy_check_)
      return false;
    // If there is an OOF legacy flex container, and PositionedObjects() are out
    // of document order (which is something that can happen in the legacy
    // engine when there's a fixed-positioned object inside an absolute-
    // positioned object - and we should just live with that and eventually get
    // rid of the legacy engine), we'll allow one more pass, in case there's a
    // fixed-positioend OOF flex item inside an absolutely-positioned OOF flex
    // container. Because at this point, PositionedObjects() should finally be
    // in correct document order. Only allow one more additional pass, though,
    // since we might get stuck in an infinite loop otherwise (for reasons
    // currently unknown).
    performing_extra_legacy_check_ = true;
  }
  bool candidate_added = false;
  for (LayoutObject* legacy_object : *legacy_objects) {
    if (placed_objects->Contains(legacy_object)) {
      if (!performing_extra_legacy_check_ || !legacy_object->NeedsLayout())
        continue;
      container_builder_->RemoveOldLegacyOOFFlexItem(*legacy_object);
    }

    // Flex OOF children may have center alignment or similar, and in order
    // to determine their static position correctly need to have a valid
    // size first.
    // We perform a pre-layout to correctly determine the static position.
    // Copied from LayoutBlock::LayoutPositionedObject
    // TODO(layout-dev): Remove this once LayoutFlexibleBox is removed.
    LayoutBox* layout_box = To<LayoutBox>(legacy_object);
    if (layout_box->Parent()->IsFlexibleBox()) {
      auto* parent = To<LayoutFlexibleBox>(layout_box->Parent());
      if (parent->SetStaticPositionForPositionedLayout(*layout_box)) {
        NGLogicalOutOfFlowPositionedNode candidate((NGBlockNode(layout_box)),
                                                   NGLogicalStaticPosition());
        NodeInfo node_info = SetupNodeInfo(candidate);
        NodeToLayout node_to_layout = {
            node_info, CalculateOffset(node_info, /* only_layout */ nullptr)};
        LayoutOOFNode(node_to_layout,
                      /* only_layout */ nullptr);
        parent->SetStaticPositionForPositionedLayout(*layout_box);
      }
    }

    // If we have a legacy OOF flex container, we'll allow some rocket science
    // to take place, as an attempt to get things laid out in correct document
    // order, or we might otherwise leave behind objects (OOF flex items)
    // needing layout.
    if (!has_legacy_flex_box_)
      has_legacy_flex_box_ = layout_box->IsFlexibleBox();

    NGLogicalStaticPosition static_position =
        LayoutBoxUtils::ComputeStaticPositionFromLegacy(
            *layout_box,
            container_builder_->Borders() + container_builder_->Scrollbar(),
            container_builder_);

    container_builder_->AddOutOfFlowLegacyCandidate(
        NGBlockNode(layout_box), static_position,
        DynamicTo<LayoutInline>(layout_box->Container()));
    candidate_added = true;
  }
  return candidate_added;
}

void NGOutOfFlowLayoutPart::HandleFragmentation(
    ColumnBalancingInfo* column_balancing_info) {
  // OOF fragmentation depends on LayoutBox data being up-to-date, which isn't
  // the case if side-effects are disabled. So we cannot safely do anything
  // here.
  if (NGDisableSideEffectsScope::IsDisabled())
    return;

  if (!column_balancing_info &&
      (!container_builder_->IsBlockFragmentationContextRoot() ||
       has_block_fragmentation_))
    return;

  // Don't use the cache if we are handling fragmentation.
  allow_first_tier_oof_cache_ = false;

  if (container_builder_->Node().IsPaginatedRoot()) {
    // Column balancing only affects multicols.
    DCHECK(!column_balancing_info);
    HeapVector<NGLogicalOutOfFlowPositionedNode> candidates;
    ClearCollectionScope<HeapVector<NGLogicalOutOfFlowPositionedNode>> scope(
        &candidates);
    container_builder_->SwapOutOfFlowPositionedCandidates(&candidates);
    // Catch everything for paged layout. We want to fragment everything. If the
    // containing block is the initial containing block, it should be fragmented
    // now, and not bubble further to the viewport (where we'd end up with
    // non-fragmented layout). Note that we're not setting a containing block
    // fragment for the candidates, as that would confuse
    // GetContainingBlockInfo(), which expects a containing block fragment to
    // also have a LayoutObject, which fragmentainers don't. Fixing that is
    // possible, but requires special-code there. This approach seems easier.
    for (NGLogicalOutOfFlowPositionedNode candidate : candidates)
      container_builder_->AddOutOfFlowFragmentainerDescendant(candidate);
  }

#if DCHECK_IS_ON()
  if (column_balancing_info) {
    DCHECK(!column_balancing_info->columns.empty());
    DCHECK(
        !column_balancing_info->out_of_flow_fragmentainer_descendants.empty());
  }
#endif
  base::AutoReset<ColumnBalancingInfo*> balancing_scope(&column_balancing_info_,
                                                        column_balancing_info);

  auto ShouldContinue = [&]() -> bool {
    if (column_balancing_info_)
      return column_balancing_info_->HasOutOfFlowFragmentainerDescendants();
    return container_builder_->HasOutOfFlowFragmentainerDescendants() ||
           container_builder_->HasMulticolsWithPendingOOFs();
  };

  while (ShouldContinue()) {
    HeapVector<NGLogicalOOFNodeForFragmentation> fragmentainer_descendants;
    ClearCollectionScope<HeapVector<NGLogicalOOFNodeForFragmentation>> scope(
        &fragmentainer_descendants);
    if (column_balancing_info_) {
      column_balancing_info_->SwapOutOfFlowFragmentainerDescendants(
          &fragmentainer_descendants);
      DCHECK(!fragmentainer_descendants.empty());
    } else {
      HandleMulticolsWithPendingOOFs(container_builder_);
      if (container_builder_->HasOutOfFlowFragmentainerDescendants()) {
        container_builder_->SwapOutOfFlowFragmentainerDescendants(
            &fragmentainer_descendants);
        DCHECK(!fragmentainer_descendants.empty());
      }
    }
    if (!fragmentainer_descendants.empty()) {
      LogicalOffset fragmentainer_progression = GetFragmentainerProgression(
          *container_builder_, GetFragmentainerType());
      LayoutFragmentainerDescendants(&fragmentainer_descendants,
                                     fragmentainer_progression);
    }
  }
  if (!column_balancing_info_) {
    for (auto& descendant : delayed_descendants_)
      container_builder_->AddOutOfFlowFragmentainerDescendant(descendant);
  }
}

// Retrieve the stored ContainingBlockInfo needed for placing positioned nodes.
// When fragmenting, the ContainingBlockInfo is not stored ahead of time and
// must be generated on demand. The reason being that during fragmentation, we
// wait to place positioned nodes until they've reached the fragmentation
// context root. In such cases, we cannot use default |ContainingBlockInfo|
// since the fragmentation root is not the containing block of the positioned
// nodes. Rather, we must generate their ContainingBlockInfo based on the
// |candidate.containing_block.fragment|.
const NGOutOfFlowLayoutPart::ContainingBlockInfo
NGOutOfFlowLayoutPart::GetContainingBlockInfo(
    const NGLogicalOutOfFlowPositionedNode& candidate) {
  const auto* container_object = container_builder_->GetLayoutObject();
  const auto& node_style = candidate.Node().Style();

  auto IsPlacedWithinGridArea = [&](const auto* containing_block) {
    if (!containing_block->IsLayoutNGGrid())
      return false;

    return !node_style.GridColumnStart().IsAuto() ||
           !node_style.GridColumnEnd().IsAuto() ||
           !node_style.GridRowStart().IsAuto() ||
           !node_style.GridRowEnd().IsAuto();
  };

  auto GridAreaContainingBlockInfo = [&](const LayoutNGGrid& containing_grid,
                                         const NGGridLayoutData& layout_data,
                                         const NGBoxStrut& borders,
                                         const LogicalSize& size)
      -> NGOutOfFlowLayoutPart::ContainingBlockInfo {
    const auto& grid_style = containing_grid.StyleRef();
    GridItemData grid_item(candidate.Node(), grid_style);

    return {grid_style.GetWritingDirection(),
            NGGridLayoutAlgorithm::ComputeOutOfFlowItemContainingRect(
                containing_grid.CachedPlacementData(), layout_data, grid_style,
                borders, size, &grid_item)};
  };

  if (candidate.inline_container.container) {
    const auto it =
        containing_blocks_map_.find(candidate.inline_container.container);
    DCHECK(it != containing_blocks_map_.end());
    return it->value;
  }

  if (candidate.is_for_fragmentation) {
    NGLogicalOOFNodeForFragmentation fragmentainer_descendant =
        To<NGLogicalOOFNodeForFragmentation>(candidate);
    if (fragmentainer_descendant.containing_block.Fragment()) {
      DCHECK(container_builder_->IsBlockFragmentationContextRoot());

      const NGPhysicalFragment* containing_block_fragment =
          fragmentainer_descendant.containing_block.Fragment();
      const LayoutObject* containing_block =
          containing_block_fragment->GetLayoutObject();
      DCHECK(containing_block);

      bool is_placed_within_grid_area =
          IsPlacedWithinGridArea(containing_block);
      auto it = containing_blocks_map_.find(containing_block);
      if (it != containing_blocks_map_.end() && !is_placed_within_grid_area)
        return it->value;

      const auto writing_direction =
          containing_block->StyleRef().GetWritingDirection();
      LogicalSize size = containing_block_fragment->Size().ConvertToLogical(
          writing_direction.GetWritingMode());
      size.block_size =
          LayoutBoxUtils::TotalBlockSize(*To<LayoutBox>(containing_block));

      // TODO(1079031): This should eventually include scrollbar and border.
      NGBoxStrut border = To<NGPhysicalBoxFragment>(containing_block_fragment)
                              ->Borders()
                              .ConvertToLogical(writing_direction);

      if (is_placed_within_grid_area) {
        return GridAreaContainingBlockInfo(
            *To<LayoutNGGrid>(containing_block),
            *To<LayoutNGGrid>(containing_block)->GridLayoutData(), border,
            size);
      }

      LogicalSize content_size = ShrinkLogicalSize(size, border);
      LogicalOffset container_offset =
          LogicalOffset(border.inline_start, border.block_start);
      container_offset += fragmentainer_descendant.containing_block.Offset();

      ContainingBlockInfo containing_block_info{
          writing_direction, LogicalRect(container_offset, content_size),
          fragmentainer_descendant.containing_block.RelativeOffset(),
          fragmentainer_descendant.containing_block.Offset()};

      return containing_blocks_map_
          .insert(containing_block, containing_block_info)
          .stored_value->value;
    }
  }

  if (IsPlacedWithinGridArea(container_object)) {
    return GridAreaContainingBlockInfo(
        *To<LayoutNGGrid>(container_object),
        container_builder_->GridLayoutData(), container_builder_->Borders(),
        {container_builder_->InlineSize(),
         container_builder_->FragmentBlockSize()});
  }

  return node_style.GetPosition() == EPosition::kAbsolute
             ? default_containing_block_info_for_absolute_
             : default_containing_block_info_for_fixed_;
}

void NGOutOfFlowLayoutPart::ComputeInlineContainingBlocks(
    const HeapVector<NGLogicalOutOfFlowPositionedNode>& candidates) {
  InlineContainingBlockUtils::InlineContainingBlockMap
      inline_container_fragments;

  for (auto& candidate : candidates) {
    if (candidate.inline_container.container &&
        !inline_container_fragments.Contains(
            candidate.inline_container.container)) {
      InlineContainingBlockUtils::InlineContainingBlockGeometry
          inline_geometry = {};
      inline_container_fragments.insert(
          candidate.inline_container.container.Get(), inline_geometry);
    }
  }

  // Fetch the inline start/end fragment geometry.
  InlineContainingBlockUtils::ComputeInlineContainerGeometry(
      &inline_container_fragments, container_builder_);

  LogicalSize container_builder_size = container_builder_->Size();
  PhysicalSize container_builder_physical_size = ToPhysicalSize(
      container_builder_size, ConstraintSpace().GetWritingMode());
  AddInlineContainingBlockInfo(
      inline_container_fragments,
      default_containing_block_info_for_absolute_.writing_direction,
      container_builder_physical_size);
}

void NGOutOfFlowLayoutPart::ComputeInlineContainingBlocksForFragmentainer(
    const HeapVector<NGLogicalOOFNodeForFragmentation>& descendants) {
  struct InlineContainingBlockInfo {
    InlineContainingBlockUtils::InlineContainingBlockMap map;
    // The relative offset of the inline's containing block to the
    // fragmentation context root.
    LogicalOffset relative_offset;
    // The offset of the containing block relative to the fragmentation context
    // root (not including any relative offset).
    LogicalOffset offset_to_fragmentation_context;
  };

  HeapHashMap<Member<const LayoutBox>, InlineContainingBlockInfo>
      inline_containg_blocks;

  // Collect the inline containers by shared containing block.
  for (auto& descendant : descendants) {
    if (descendant.inline_container.container) {
      DCHECK(descendant.containing_block.Fragment());
      const LayoutBox* containing_block = To<LayoutBox>(
          descendant.containing_block.Fragment()->GetLayoutObject());

      InlineContainingBlockUtils::InlineContainingBlockGeometry
          inline_geometry = {};
      inline_geometry.relative_offset =
          descendant.inline_container.relative_offset;
      auto it = inline_containg_blocks.find(containing_block);
      if (it != inline_containg_blocks.end()) {
        if (!it->value.map.Contains(descendant.inline_container.container)) {
          it->value.map.insert(descendant.inline_container.container.Get(),
                               inline_geometry);
        }
        continue;
      }
      InlineContainingBlockUtils::InlineContainingBlockMap inline_containers;
      inline_containers.insert(descendant.inline_container.container.Get(),
                               inline_geometry);
      InlineContainingBlockInfo inline_info{
          inline_containers, descendant.containing_block.RelativeOffset(),
          descendant.containing_block.Offset()};
      inline_containg_blocks.insert(containing_block, inline_info);
    }
  }

  for (auto& inline_containg_block : inline_containg_blocks) {
    const LayoutBox* containing_block = inline_containg_block.key;
    InlineContainingBlockInfo& inline_info = inline_containg_block.value;

    LogicalSize size(LayoutBoxUtils::InlineSize(*containing_block),
                     LayoutBoxUtils::TotalBlockSize(*containing_block));
    PhysicalSize container_builder_physical_size =
        ToPhysicalSize(size, containing_block->StyleRef().GetWritingMode());

    // Fetch the inline start/end fragment geometry.
    InlineContainingBlockUtils::ComputeInlineContainerGeometryForFragmentainer(
        containing_block, container_builder_physical_size, &inline_info.map);

    AddInlineContainingBlockInfo(
        inline_info.map, containing_block->StyleRef().GetWritingDirection(),
        container_builder_physical_size, inline_info.relative_offset,
        inline_info.offset_to_fragmentation_context,
        /* adjust_for_fragmentation */ true);
  }
}

void NGOutOfFlowLayoutPart::AddInlineContainingBlockInfo(
    const InlineContainingBlockUtils::InlineContainingBlockMap&
        inline_container_fragments,
    const WritingDirectionMode container_writing_direction,
    PhysicalSize container_builder_size,
    LogicalOffset containing_block_relative_offset,
    LogicalOffset containing_block_offset,
    bool adjust_for_fragmentation) {
  // Transform the start/end fragments into a ContainingBlockInfo.
  for (const auto& block_info : inline_container_fragments) {
    DCHECK(block_info.value.has_value());

    // The calculation below determines the size of the inline containing block
    // rect.
    //
    // To perform this calculation we:
    // 1. Determine the start_offset "^", this is at the logical-start (wrt.
    //    default containing block), of the start fragment rect.
    // 2. Determine the end_offset "$", this is at the logical-end (wrt.
    //    default containing block), of the end  fragment rect.
    // 3. Determine the logical rectangle defined by these two offsets.
    //
    // Case 1a: Same direction, overlapping fragments.
    //      +---------------
    // ---> |^*****-------->
    //      +*----*---------
    //       *    *
    // ------*----*+
    // ----> *****$| --->
    // ------------+
    //
    // Case 1b: Different direction, overlapping fragments.
    //      +---------------
    // ---> ^******* <-----|
    //      *------*--------
    //      *      *
    // -----*------*
    // |<-- *******$ --->
    // ------------+
    //
    // Case 2a: Same direction, non-overlapping fragments.
    //             +--------
    // --------->  |^ ----->
    //             +*-------
    //              *
    // --------+    *
    // ------->|    $ --->
    // --------+
    //
    // Case 2b: Same direction, non-overlapping fragments.
    //             +--------
    // --------->  ^ <-----|
    //             *--------
    //             *
    // --------+   *
    // | <------   $  --->
    // --------+
    //
    // Note in cases [1a, 2a] we need to account for the inline borders of the
    // rectangles, where-as in [1b, 2b] we do not. This is handled by the
    // is_same_direction check(s).
    //
    // Note in cases [2a, 2b] we don't allow a "negative" containing block size,
    // we clamp negative sizes to zero.
    const ComputedStyle* inline_cb_style = block_info.key->Style();
    DCHECK(inline_cb_style);

    const auto inline_writing_direction =
        inline_cb_style->GetWritingDirection();
    NGBoxStrut inline_cb_borders = ComputeBordersForInline(*inline_cb_style);
    DCHECK_EQ(container_writing_direction.GetWritingMode(),
              inline_writing_direction.GetWritingMode());

    bool is_same_direction =
        container_writing_direction == inline_writing_direction;

    // Step 1 - determine the start_offset.
    const PhysicalRect& start_rect =
        block_info.value->start_fragment_union_rect;
    LogicalOffset start_offset = start_rect.offset.ConvertToLogical(
        container_writing_direction, container_builder_size, start_rect.size);

    // Make sure we add the inline borders, we don't need to do this in the
    // inline direction if the blocks are in opposite directions.
    start_offset.block_offset += inline_cb_borders.block_start;
    if (is_same_direction)
      start_offset.inline_offset += inline_cb_borders.inline_start;

    // Step 2 - determine the end_offset.
    const PhysicalRect& end_rect = block_info.value->end_fragment_union_rect;
    LogicalOffset end_offset = end_rect.offset.ConvertToLogical(
        container_writing_direction, container_builder_size, end_rect.size);

    // Add in the size of the fragment to get the logical end of the fragment.
    end_offset += end_rect.size.ConvertToLogical(
        container_writing_direction.GetWritingMode());

    // Make sure we subtract the inline borders, we don't need to do this in the
    // inline direction if the blocks are in opposite directions.
    end_offset.block_offset -= inline_cb_borders.block_end;
    if (is_same_direction)
      end_offset.inline_offset -= inline_cb_borders.inline_end;

    // Make sure we don't end up with a rectangle with "negative" size.
    end_offset.inline_offset =
        std::max(end_offset.inline_offset, start_offset.inline_offset);
    end_offset.block_offset =
        std::max(end_offset.block_offset, start_offset.block_offset);

    // Step 3 - determine the logical rectangle.

    // Determine the logical size of the containing block.
    LogicalSize inline_cb_size = {
        end_offset.inline_offset - start_offset.inline_offset,
        end_offset.block_offset - start_offset.block_offset};
    DCHECK_GE(inline_cb_size.inline_size, LayoutUnit());
    DCHECK_GE(inline_cb_size.block_size, LayoutUnit());

    if (adjust_for_fragmentation) {
      // When fragmenting, the containing block will not be associated with the
      // current builder. Thus, we need to adjust the start offset to take the
      // writing mode of the builder into account.
      PhysicalSize physical_size =
          ToPhysicalSize(inline_cb_size, ConstraintSpace().GetWritingMode());
      start_offset =
          start_offset
              .ConvertToPhysical(container_writing_direction,
                                 container_builder_size, physical_size)
              .ConvertToLogical(ConstraintSpace().GetWritingDirection(),
                                container_builder_size, physical_size);
    }

    // Subtract out the inline relative offset, if set, so that it can be
    // applied after fragmentation is performed on the fragmentainer
    // descendants.
    DCHECK((block_info.value->relative_offset == LogicalOffset() &&
            containing_block_relative_offset == LogicalOffset() &&
            containing_block_offset == LogicalOffset()) ||
           container_builder_->IsBlockFragmentationContextRoot());
    LogicalOffset container_offset =
        start_offset - block_info.value->relative_offset;
    LogicalOffset total_relative_offset =
        containing_block_relative_offset + block_info.value->relative_offset;

    // The offset of the container is currently relative to the containing
    // block. Add the offset of the containng block to the fragmentation context
    // root so that it is relative to the fragmentation context root, instead.
    container_offset += containing_block_offset;

    // If an OOF has an inline containing block, the OOF offset that is written
    // back to legacy is relative to the containing block of the inline rather
    // than the inline itself. |containing_block_offset| will be used when
    // calculating this OOF offset. However, there may be some relative offset
    // between the containing block and the inline container that should be
    // included in the final OOF offset that is written back to legacy. Adjust
    // for that relative offset here.
    containing_blocks_map_.insert(
        block_info.key.Get(),
        ContainingBlockInfo{
            inline_writing_direction,
            LogicalRect(container_offset, inline_cb_size),
            total_relative_offset,
            containing_block_offset - block_info.value->relative_offset});
  }
}

void NGOutOfFlowLayoutPart::LayoutCandidates(
    HeapVector<NGLogicalOutOfFlowPositionedNode>* candidates,
    const LayoutBox* only_layout,
    HeapHashSet<Member<const LayoutObject>>* placed_objects) {
  while (candidates->size() > 0) {
    if (!has_block_fragmentation_ ||
        container_builder_->IsInitialColumnBalancingPass())
      ComputeInlineContainingBlocks(*candidates);
    for (auto& candidate : *candidates) {
      LayoutBox* layout_box = candidate.box;
      if (!container_builder_->IsBlockFragmentationContextRoot())
        SaveStaticPositionOnPaintLayer(layout_box, candidate.static_position);
      if (IsContainingBlockForCandidate(candidate) &&
          (!only_layout || layout_box == only_layout)) {
        if (has_block_fragmentation_) {
          container_builder_->SetHasOutOfFlowInFragmentainerSubtree(true);
          if (!container_builder_->IsInitialColumnBalancingPass()) {
            // As an optimization, only populate legacy positioned objects lists
            // when inside a fragmentation context root, since otherwise we can
            // just look at the children in the fragment tree.
            if (layout_box != only_layout) {
              container_builder_->InsertLegacyPositionedObject(
                  candidate.Node());
            }
            NGLogicalOOFNodeForFragmentation fragmentainer_descendant(
                candidate);
            container_builder_->AdjustFragmentainerDescendant(
                fragmentainer_descendant);
            container_builder_
                ->AdjustFixedposContainingBlockForInnerMulticols();
            container_builder_->AddOutOfFlowFragmentainerDescendant(
                fragmentainer_descendant);
            continue;
          }
        }
        NodeInfo node_info = SetupNodeInfo(candidate);
        NodeToLayout node_to_layout = {node_info,
                                       CalculateOffset(node_info, only_layout)};
        const NGLayoutResult* result =
            LayoutOOFNode(node_to_layout, only_layout);
        container_builder_->AddResult(
            *result, result->OutOfFlowPositionedOffset(),
            /* relative_offset */ absl::nullopt, &candidate.inline_container);
        container_builder_->SetHasOutOfFlowFragmentChild(true);
        if (container_builder_->IsInitialColumnBalancingPass()) {
          container_builder_->PropagateTallestUnbreakableBlockSize(
              result->TallestUnbreakableBlockSize());
        }
        placed_objects->insert(layout_box);
      } else {
        container_builder_->AddOutOfFlowDescendant(candidate);
      }
    }
    // Sweep any candidates that might have been added.
    // This happens when an absolute container has a fixed child.
    candidates->Shrink(0);
    container_builder_->SwapOutOfFlowPositionedCandidates(candidates);
  }
}

void NGOutOfFlowLayoutPart::HandleMulticolsWithPendingOOFs(
    NGBoxFragmentBuilder* container_builder) {
  if (!container_builder->HasMulticolsWithPendingOOFs())
    return;

  NGContainerFragmentBuilder::MulticolCollection multicols_with_pending_oofs;
  container_builder->SwapMulticolsWithPendingOOFs(&multicols_with_pending_oofs);
  DCHECK(!multicols_with_pending_oofs.empty());

  while (!multicols_with_pending_oofs.empty()) {
    for (auto& multicol : multicols_with_pending_oofs)
      LayoutOOFsInMulticol(NGBlockNode(multicol.key), multicol.value);
    multicols_with_pending_oofs.clear();
    container_builder->SwapMulticolsWithPendingOOFs(
        &multicols_with_pending_oofs);
  }
}

void NGOutOfFlowLayoutPart::LayoutOOFsInMulticol(
    const NGBlockNode& multicol,
    const NGMulticolWithPendingOOFs<LogicalOffset>* multicol_info) {
  HeapVector<NGLogicalOOFNodeForFragmentation> oof_nodes_to_layout;
  ClearCollectionScope<HeapVector<NGLogicalOOFNodeForFragmentation>>
      oof_nodes_scope(&oof_nodes_to_layout);
  HeapVector<MulticolChildInfo> multicol_children;
  ClearCollectionScope<HeapVector<MulticolChildInfo>> multicol_scope(
      &multicol_children);

  const NGBlockBreakToken* current_column_break_token = nullptr;
  const NGBlockBreakToken* previous_multicol_break_token = nullptr;

  LayoutUnit column_inline_progression = kIndefiniteSize;
  LogicalOffset multicol_offset = multicol_info->multicol_offset;

  // Create a simplified container builder for multicol children. It cannot be
  // used to generate a fragment (since no size has been set, for one), but is
  // suitable for holding child fragmentainers while we're cloning them.
  NGConstraintSpace limited_multicol_constraint_space =
      CreateConstraintSpaceForMulticol(multicol);
  NGFragmentGeometry limited_fragment_geometry =
      CalculateInitialFragmentGeometry(limited_multicol_constraint_space,
                                       multicol, /* break_token */ nullptr);
  NGBoxFragmentBuilder limited_multicol_container_builder =
      CreateContainerBuilderForMulticol(multicol,
                                        limited_multicol_constraint_space,
                                        limited_fragment_geometry);
  // The block size that we set on the multicol builder doesn't matter since
  // we only care about the size of the fragmentainer children when laying out
  // the remaining OOFs.
  limited_multicol_container_builder.SetFragmentsTotalBlockSize(LayoutUnit());

  limited_multicol_container_builder.SetDisableOOFDescendantsPropagation();

  WritingDirectionMode writing_direction =
      multicol.Style().GetWritingDirection();
  const NGPhysicalBoxFragment* last_fragment_with_fragmentainer = nullptr;

  // Accumulate all of the pending OOF positioned nodes that are stored inside
  // |multicol|.
  for (auto& multicol_fragment : multicol.GetLayoutBox()->PhysicalFragments()) {
    const NGPhysicalBoxFragment* multicol_box_fragment =
        To<NGPhysicalBoxFragment>(&multicol_fragment);

    const ComputedStyle& style = multicol_box_fragment->Style();
    const WritingModeConverter converter(writing_direction,
                                         multicol_box_fragment->Size());
    wtf_size_t current_column_index = kNotFound;

    if (column_inline_progression == kIndefiniteSize) {
      // TODO(almaher): This should eventually include scrollbar, as well.
      NGBoxStrut border_padding =
          multicol_box_fragment->Borders().ConvertToLogical(writing_direction) +
          multicol_box_fragment->Padding().ConvertToLogical(writing_direction);
      LayoutUnit available_inline_size =
          multicol_box_fragment->Size()
              .ConvertToLogical(writing_direction.GetWritingMode())
              .inline_size -
          border_padding.InlineSum();
      column_inline_progression =
          ColumnInlineProgression(available_inline_size, style);
    }

    // Collect the children of the multicol fragments.
    for (auto& child :
         multicol_box_fragment->GetMutableChildrenForOutOfFlow().Children()) {
      const auto* fragment = child.get();
      LogicalOffset offset =
          converter.ToLogical(child.Offset(), fragment->Size());
      if (fragment->IsFragmentainerBox()) {
        current_column_break_token =
            To<NGBlockBreakToken>(fragment->BreakToken());
        current_column_index = multicol_children.size();
        last_fragment_with_fragmentainer = multicol_box_fragment;
      }

      limited_multicol_container_builder.AddChild(
          *fragment, offset, /* margin_strut */ nullptr,
          /* is_self_collapsing */ false, /* relative_offset */ absl::nullopt,
          /* inline_container */ nullptr,
          /* adjustment_for_oof_propagation */ absl::nullopt);
      multicol_children.emplace_back(MulticolChildInfo(&child));
    }

    // If a column fragment is updated with OOF children, we may need to update
    // the reference to its break token in its parent's break token. There
    // should be at most one column break token per parent break token
    // (representing the last column laid out in that fragment). Thus, search
    // for |current_column_break_token| in |multicol_box_fragment|'s list of
    // child break tokens and update the stored MulticolChildInfo if found.
    const NGBlockBreakToken* break_token = multicol_box_fragment->BreakToken();
    if (current_column_index != kNotFound && break_token &&
        break_token->ChildBreakTokens().size()) {
      // If there is a column break token, it will be the last item in its
      // parent's list of break tokens.
      const auto children = break_token->ChildBreakTokens();
      const NGBlockBreakToken* child_token =
          To<NGBlockBreakToken>(children[children.size() - 1].Get());
      if (child_token == current_column_break_token) {
        MulticolChildInfo& child_info = multicol_children[current_column_index];
        child_info.parent_break_token = break_token;
      }
    }

    // Convert the OOF fragmentainer descendants to the logical coordinate space
    // and store the resulting nodes inside |oof_nodes_to_layout|.
    for (const auto& descendant :
         NGFragmentedOutOfFlowData::OutOfFlowPositionedFragmentainerDescendants(
             *multicol_box_fragment)) {
      if (oof_nodes_to_layout.empty() &&
          multicol_info->fixedpos_containing_block.Fragment() &&
          previous_multicol_break_token) {
        // At this point, the multicol offset is the offset from the fixedpos
        // containing block to the first multicol fragment holding OOF
        // fragmentainer descendants. Update this offset such that it is the
        // offset from the fixedpos containing block to the top of the first
        // multicol fragment.
        multicol_offset.block_offset -=
            previous_multicol_break_token->ConsumedBlockSize();
      }
      const NGPhysicalFragment* containing_block_fragment =
          descendant.containing_block.Fragment();
      // If the containing block is not set, that means that the inner multicol
      // was its containing block, and the OOF will be laid out elsewhere.
      if (!containing_block_fragment)
        continue;
      LogicalOffset containing_block_offset =
          converter.ToLogical(descendant.containing_block.Offset(),
                              containing_block_fragment->Size());
      LogicalOffset containing_block_rel_offset =
          converter.ToLogical(descendant.containing_block.RelativeOffset(),
                              containing_block_fragment->Size());

      const NGPhysicalFragment* fixedpos_containing_block_fragment =
          descendant.fixedpos_containing_block.Fragment();
      LogicalOffset fixedpos_containing_block_offset;
      LogicalOffset fixedpos_containing_block_rel_offset;
      if (fixedpos_containing_block_fragment) {
        fixedpos_containing_block_offset =
            converter.ToLogical(descendant.fixedpos_containing_block.Offset(),
                                fixedpos_containing_block_fragment->Size());
        fixedpos_containing_block_rel_offset = converter.ToLogical(
            descendant.fixedpos_containing_block.RelativeOffset(),
            fixedpos_containing_block_fragment->Size());
      }

      NGInlineContainer<LogicalOffset> inline_container(
          descendant.inline_container.container,
          converter.ToLogical(descendant.inline_container.relative_offset,
                              PhysicalSize()));

      NGInlineContainer<LogicalOffset> fixedpos_inline_container(
          descendant.fixedpos_inline_container.container,
          converter.ToLogical(
              descendant.fixedpos_inline_container.relative_offset,
              PhysicalSize()));

      // The static position should remain relative to its containing block
      // fragment.
      const WritingModeConverter containing_block_converter(
          writing_direction, containing_block_fragment->Size());
      NGLogicalStaticPosition static_position =
          descendant.StaticPosition().ConvertToLogical(
              containing_block_converter);

      NGLogicalOOFNodeForFragmentation node = {
          descendant.Node(),
          static_position,
          inline_container,
          NGContainingBlock<LogicalOffset>(
              containing_block_offset, containing_block_rel_offset,
              containing_block_fragment,
              descendant.containing_block.IsInsideColumnSpanner(),
              descendant.containing_block.RequiresContentBeforeBreaking()),
          NGContainingBlock<LogicalOffset>(
              fixedpos_containing_block_offset,
              fixedpos_containing_block_rel_offset,
              fixedpos_containing_block_fragment,
              descendant.fixedpos_containing_block.IsInsideColumnSpanner(),
              descendant.fixedpos_containing_block
                  .RequiresContentBeforeBreaking()),
          fixedpos_inline_container};
      oof_nodes_to_layout.push_back(node);
    }
    previous_multicol_break_token = break_token;
  }
  // When an OOF's CB is a spanner (or a descendant of a spanner), we will lay
  // out the OOF at the next fragmentation context root ancestor. As such, we
  // remove any such OOF nodes from the nearest multicol's list of OOF
  // descendants during OOF node propagation, which may cause
  // |oof_nodes_to_layout| to be empty. Return early if this is the case.
  if (oof_nodes_to_layout.empty())
    return;

  DCHECK(!limited_multicol_container_builder
              .HasOutOfFlowFragmentainerDescendants());

  wtf_size_t old_fragment_count =
      limited_multicol_container_builder.Children().size();

  LogicalOffset fragmentainer_progression(column_inline_progression,
                                          LayoutUnit());

  // Layout the OOF positioned elements inside the inner multicol.
  NGOutOfFlowLayoutPart inner_part(multicol, limited_multicol_constraint_space,
                                   &limited_multicol_container_builder);
  inner_part.allow_first_tier_oof_cache_ = false;
  inner_part.outer_container_builder_ =
      outer_container_builder_ ? outer_container_builder_ : container_builder_;
  inner_part.LayoutFragmentainerDescendants(
      &oof_nodes_to_layout, fragmentainer_progression,
      multicol_info->fixedpos_containing_block.Fragment(), &multicol_children);

  wtf_size_t new_fragment_count =
      limited_multicol_container_builder.Children().size();

  if (old_fragment_count != new_fragment_count) {
    DCHECK_GT(new_fragment_count, old_fragment_count);
    // We created additional fragmentainers to hold OOFs, and this is in a
    // nested fragmentation context. This means that the multicol fragment has
    // already been created, and we will therefore need to replace one of those
    // fragments. Locate the last multicol container fragment that already has
    // fragmentainers, and append all new fragmentainers there. Note that this
    // means that we may end up with more inner fragmentainers than what we
    // actually have room for (so that they'll overflow in the inline
    // direction), because we don't attempt to put fragmentainers into
    // additional multicol fragments in outer fragmentainers. This is an
    // implementation limitation which we can hopefully live with.
    DCHECK(last_fragment_with_fragmentainer);
    LayoutBox& box = *last_fragment_with_fragmentainer->MutableOwnerLayoutBox();
    wtf_size_t fragment_count = box.PhysicalFragmentCount();
    DCHECK_GE(fragment_count, 1u);
    const NGLayoutResult* old_result = nullptr;
    wtf_size_t fragment_idx = fragment_count - 1;
    do {
      old_result = box.GetLayoutResult(fragment_idx);
      if (&old_result->PhysicalFragment() == last_fragment_with_fragmentainer)
        break;
      DCHECK_GT(fragment_idx, 0u);
      fragment_idx--;
    } while (true);

    // We have located the right multicol fragment to replace. Re-use its old
    // constraint space and establish a layout algorithm to regenerate the
    // fragment.
    const NGConstraintSpace& constraint_space =
        old_result->GetConstraintSpaceForCaching();
    NGFragmentGeometry fragment_geometry = CalculateInitialFragmentGeometry(
        constraint_space, multicol, /* break_token */ nullptr);
    NGLayoutAlgorithmParams params(multicol, fragment_geometry,
                                   constraint_space);
    NGSimplifiedLayoutAlgorithm algorithm(params, *old_result,
                                          /* keep_old_size */ true);

    // First copy the fragmentainers (and other child fragments) that we already
    // had.
    algorithm.CloneOldChildren();

    WritingModeConverter converter(constraint_space.GetWritingDirection(),
                                   old_result->PhysicalFragment().Size());
    LayoutUnit additional_column_block_size;
    // Then append the new fragmentainers.
    for (wtf_size_t i = old_fragment_count; i < new_fragment_count; i++) {
      const NGLogicalLink& child =
          limited_multicol_container_builder.Children()[i];
      algorithm.AppendNewChildFragment(*child.fragment, child.offset);
      additional_column_block_size +=
          converter.ToLogical(child.fragment->Size()).block_size;
    }

    // We've already written back to legacy for |multicol|, but if we added
    // new columns to hold any OOF descendants, we need to extend the final
    // size of the legacy flow thread to encompass those new columns.
    multicol.MakeRoomForExtraColumns(additional_column_block_size);

    // Create a new multicol container fragment and replace all references to
    // the old one with this new one.
    const NGLayoutResult* new_result =
        algorithm.CreateResultAfterManualChildLayout();
    ReplaceFragment(std::move(new_result), *last_fragment_with_fragmentainer,
                    fragment_idx);
  }

  // Any descendants should have been handled in
  // LayoutFragmentainerDescendants(). However, if there were any candidates
  // found, pass them back to |container_builder_| so they can continue
  // propagating up the tree.
  DCHECK(
      !limited_multicol_container_builder.HasOutOfFlowPositionedDescendants());
  DCHECK(!limited_multicol_container_builder
              .HasOutOfFlowFragmentainerDescendants());
  limited_multicol_container_builder.TransferOutOfFlowCandidates(
      container_builder_, multicol_offset, multicol_info);

  // Handle any inner multicols with OOF descendants that may have propagated up
  // while laying out the direct OOF descendants of the current multicol.
  HandleMulticolsWithPendingOOFs(&limited_multicol_container_builder);
}

void NGOutOfFlowLayoutPart::LayoutFragmentainerDescendants(
    HeapVector<NGLogicalOOFNodeForFragmentation>* descendants,
    LogicalOffset fragmentainer_progression,
    bool outer_context_has_fixedpos_container,
    HeapVector<MulticolChildInfo>* multicol_children) {
  multicol_children_ = multicol_children;
  outer_context_has_fixedpos_container_ = outer_context_has_fixedpos_container;
  DCHECK(multicol_children_ || !outer_context_has_fixedpos_container_);

  original_column_block_size_ =
      ShrinkLogicalSize(container_builder_->InitialBorderBoxSize(),
                        container_builder_->BorderScrollbarPadding())
          .block_size;

  NGLogicalAnchorQueryForFragmentation stitched_anchor_queries;
  NGBoxFragmentBuilder* builder_for_anchor_query = container_builder_;
  if (outer_container_builder_) {
    // If this is an inner layout of the nested block fragmentation, and if this
    // block fragmentation context is block fragmented, |multicol_children|
    // doesn't have correct block offsets of fragmentainers anchor query needs.
    // Calculate the anchor query from the outer block fragmentation context
    // instead in order to get the correct offsets.
    for (const MulticolChildInfo& multicol_child : *multicol_children) {
      if (multicol_child.parent_break_token) {
        builder_for_anchor_query = outer_container_builder_;
        break;
      }
    }
  }
  stitched_anchor_queries.Update(
      builder_for_anchor_query->Children(), *descendants,
      *builder_for_anchor_query->Node().GetLayoutBox(),
      builder_for_anchor_query->GetWritingDirection());

  // |descendants| are sorted by fragmentainers, and then by the layout order,
  // which is pre-order of the box tree. When fragments are pushed to later
  // fragmentainers by overflow, |descendants| need to be re-sorted by the
  // pre-order. Note that both |SortInPreOrder| and |IsInPreOrder| are not
  // cheap, limit only when needed.
  if (stitched_anchor_queries.HasAnchorsOnOutOfFlowObjects() &&
      !IsInPreOrder(*descendants)) {
    SortInPreOrder(descendants);
  }

  HeapVector<HeapVector<NodeToLayout>> descendants_to_layout;
  ClearCollectionScope<HeapVector<HeapVector<NodeToLayout>>>
      descendants_to_layout_scope(&descendants_to_layout);
  while (descendants->size() > 0) {
    ComputeInlineContainingBlocksForFragmentainer(*descendants);

    // When there are anchor queries, each containing block should be laid out
    // separately. This loop chunks |descendants| by their containing blocks, if
    // they have anchor queries.
    base::span<NGLogicalOOFNodeForFragmentation> descendants_span =
        base::make_span(*descendants);
    for (;;) {
      bool has_new_descendants_span = false;
      // The CSS containing block of the last descendant, to group |descendants|
      // by the CSS containing block.
      const LayoutObject* last_css_containing_block = nullptr;
      const NGLogicalAnchorQuery* stitched_anchor_query =
          &NGLogicalAnchorQuery::Empty();
      DCHECK(stitched_anchor_query);

      // Sort the descendants by fragmentainer index in |descendants_to_layout|.
      // This will ensure that the descendants are laid out in the correct
      // order.
      DCHECK(!descendants_span.empty());
      for (auto& descendant : descendants_span) {
        if (GetFragmentainerType() == kFragmentColumn) {
          auto* containing_block = To<LayoutBox>(
              descendant.containing_block.Fragment()->GetLayoutObject());
          DCHECK(containing_block);

          // We may try to lay out an OOF once we reach a column spanner or when
          // column balancing. However, if the containing block has not finished
          // layout, we should wait to lay out the OOF in case its position is
          // dependent on its containing block's final size.
          if (containing_block->PhysicalFragments().back().BreakToken()) {
            delayed_descendants_.push_back(descendant);
            continue;
          }
        }

        // Ensure each containing block is laid out before laying out other
        // containing blocks. The CSS Anchor Positioning may evaluate
        // differently when the containing block is different, and may refer to
        // other containing blocks that were already laid out.
        //
        // Use |LayoutObject::Container|, not |LayoutObject::ContainingBlock|.
        // The latter is not the CSS containing block for inline boxes. See the
        // comment of |LayoutObject::ContainingBlock|.
        //
        // Note |descendant.containing_block.fragment| is |ContainingBlock|, not
        // the CSS containing block.
        DCHECK(stitched_anchor_query);
        if (stitched_anchor_queries.ShouldLayoutByContainingBlock()) {
          const LayoutObject* css_containing_block =
              descendant.box->Container();
          DCHECK(css_containing_block);
          if (css_containing_block != last_css_containing_block) {
            // Chunking the layout of OOFs by the containing blocks is done only
            // if it has anchor query, for the performance reasons to minimize
            // the number of rebuilding fragmentainer fragments.
            if (last_css_containing_block &&
                (!stitched_anchor_query->IsEmpty() ||
                 stitched_anchor_queries.HasAnchorsOnOutOfFlowObjects())) {
              has_new_descendants_span = true;
              descendants_span = descendants_span.subspan(
                  &descendant - descendants_span.data());
              break;
            }
            last_css_containing_block = css_containing_block;
            stitched_anchor_query =
                &stitched_anchor_queries.StitchedAnchorQuery(
                    *css_containing_block);
            DCHECK(stitched_anchor_query);
          }
        }

        NodeInfo node_info = SetupNodeInfo(descendant);
        NodeToLayout node_to_layout = {
            node_info,
            CalculateOffset(node_info, /* only_layout */ nullptr,
                            /* is_first_run */ true, stitched_anchor_query)};
        node_to_layout.containing_block_fragment =
            descendant.containing_block.Fragment();
        node_to_layout.offset_info.original_offset =
            node_to_layout.offset_info.offset;

        DCHECK(node_to_layout.offset_info.block_estimate);

        // Determine in which fragmentainer this OOF element will start its
        // layout and adjust the offset to be relative to that fragmentainer.
        wtf_size_t start_index = 0;
        ComputeStartFragmentIndexAndRelativeOffset(
            node_info.default_writing_direction.GetWritingMode(),
            *node_to_layout.offset_info.block_estimate, &start_index,
            &node_to_layout.offset_info.offset);
        if (start_index >= descendants_to_layout.size())
          descendants_to_layout.resize(start_index + 1);
        descendants_to_layout[start_index].emplace_back(node_to_layout);
      }

      HeapVector<NodeToLayout> fragmented_descendants;
      ClearCollectionScope<HeapVector<NodeToLayout>>
          fragmented_descendants_scope(&fragmented_descendants);
      fragmentainer_consumed_block_size_ = LayoutUnit();
      auto& children = FragmentationContextChildren();
      wtf_size_t num_children = children.size();

      // Layout the OOF descendants in order of fragmentainer index.
      for (wtf_size_t index = 0; index < descendants_to_layout.size();
           index++) {
        const NGPhysicalFragment* fragment = nullptr;
        if (index < num_children)
          fragment = children[index].fragment;
        else if (column_balancing_info_)
          column_balancing_info_->num_new_columns++;

        // Skip over any column spanners.
        if (!fragment || fragment->IsFragmentainerBox()) {
          HeapVector<NodeToLayout>& pending_descendants =
              descendants_to_layout[index];
          bool is_last_fragmentainer_with_oof_descendants =
              index + 1 == descendants_to_layout.size();
          LayoutOOFsInFragmentainer(pending_descendants, index,
                                    fragmentainer_progression,
                                    is_last_fragmentainer_with_oof_descendants,
                                    &fragmented_descendants);
          // Retrieve the updated or newly added fragmentainer, and add its
          // block contribution to the consumed block size. Skip this if we are
          // column balancing, though, since this is only needed when adding
          // OOFs to the builder in the true layout pass.
          if (!column_balancing_info_) {
            fragment = children[index].fragment;
            fragmentainer_consumed_block_size_ +=
                fragment->Size()
                    .ConvertToLogical(
                        container_builder_->Style().GetWritingMode())
                    .block_size;
          }
        }

        // Extend |descendants_to_layout| if an OOF element fragments into a
        // fragmentainer at an index that does not yet exist in
        // |descendants_to_layout|.
        if (index == descendants_to_layout.size() - 1 &&
            !fragmented_descendants.empty())
          descendants_to_layout.resize(index + 2);
      }
      descendants_to_layout.Shrink(0);

      // When laying out OOFs by containing blocks, and there are more
      // containing blocks, update anchor queries and layout OOFs in the next
      // containing block.
      if (!has_new_descendants_span)
        break;
      stitched_anchor_queries.Update(
          builder_for_anchor_query->Children(), descendants_span,
          *builder_for_anchor_query->Node().GetLayoutBox(),
          builder_for_anchor_query->GetWritingDirection());
    }

    // Sweep any descendants that might have been bubbled up from the fragment
    // to the |container_builder_|. This happens when we have nested absolute
    // position elements.
    descendants->Shrink(0);
    container_builder_->SwapOutOfFlowFragmentainerDescendants(descendants);
  }
}

NGOutOfFlowLayoutPart::NodeInfo NGOutOfFlowLayoutPart::SetupNodeInfo(
    const NGLogicalOutOfFlowPositionedNode& oof_node) {
  NGBlockNode node = oof_node.Node();
  const NGPhysicalFragment* containing_block_fragment =
      oof_node.is_for_fragmentation
          ? To<NGLogicalOOFNodeForFragmentation>(oof_node)
                .containing_block.Fragment()
          : nullptr;

#if DCHECK_IS_ON()
  const LayoutObject* container =
      containing_block_fragment ? containing_block_fragment->GetLayoutObject()
                                : container_builder_->GetLayoutObject();

  if (container) {
    // "NGOutOfFlowLayoutPart container is ContainingBlock" invariant cannot be
    // enforced for tables. Tables are special, in that the ContainingBlock is
    // TABLE, but constraint space is generated by TBODY/TR/. This happens
    // because TBODY/TR are not LayoutBlocks, but LayoutBoxModelObjects.
    DCHECK(container == node.GetLayoutBox()->ContainingBlock() ||
           node.GetLayoutBox()->ContainingBlock()->IsTable());
  } else {
    // If there's no layout object associated, the containing fragment should be
    // a page, and the containing block of the node should be the LayoutView.
    DCHECK_EQ(containing_block_fragment->BoxType(),
              NGPhysicalFragment::kPageBox);
    DCHECK_EQ(node.GetLayoutBox()->ContainingBlock(),
              node.GetLayoutBox()->View());
  }
#endif

  const ContainingBlockInfo container_info = GetContainingBlockInfo(oof_node);
  const ComputedStyle& oof_style = node.Style();
  const auto oof_writing_direction = oof_style.GetWritingDirection();

  LogicalSize container_content_size = container_info.rect.size;
  PhysicalSize container_physical_content_size = ToPhysicalSize(
      container_content_size, ConstraintSpace().GetWritingMode());

  bool requires_content_before_breaking = false;

  // Adjust the |static_position| (which is currently relative to the default
  // container's border-box). ng_absolute_utils expects the static position to
  // be relative to the container's padding-box. Since
  // |container_info.rect.offset| is relative to its fragmentainer in this
  // case, we also need to adjust the offset to account for this.
  NGLogicalStaticPosition static_position = oof_node.static_position;
  static_position.offset -= container_info.rect.offset;
  if (containing_block_fragment) {
    const auto& containing_block_for_fragmentation =
        To<NGLogicalOOFNodeForFragmentation>(oof_node).containing_block;
    static_position.offset += containing_block_for_fragmentation.Offset();
    requires_content_before_breaking =
        containing_block_for_fragmentation.RequiresContentBeforeBreaking();
  }

  NGLogicalStaticPosition oof_static_position =
      static_position
          .ConvertToPhysical({ConstraintSpace().GetWritingDirection(),
                              container_physical_content_size})
          .ConvertToLogical(
              {oof_writing_direction, container_physical_content_size});

  // Need a constraint space to resolve offsets.
  NGConstraintSpaceBuilder builder(ConstraintSpace(), oof_writing_direction,
                                   /* is_new_fc */ true);
  builder.SetAvailableSize(container_content_size);
  builder.SetPercentageResolutionSize(container_content_size);

  if (container_builder_->IsInitialColumnBalancingPass()) {
    // The |fragmentainer_offset_delta| will not make a difference in the
    // initial column balancing pass.
    SetupSpaceBuilderForFragmentation(
        ConstraintSpace(), node,
        /* fragmentainer_offset_delta */ LayoutUnit(), &builder,
        /* is_new_fc */ true,
        /* requires_content_before_breaking */ false);
  }

  NGContainingBlock<LogicalOffset> fixedpos_containing_block;
  NGInlineContainer<LogicalOffset> fixedpos_inline_container;
  if (containing_block_fragment) {
    fixedpos_containing_block = To<NGLogicalOOFNodeForFragmentation>(oof_node)
                                    .fixedpos_containing_block;
    fixedpos_inline_container = To<NGLogicalOOFNodeForFragmentation>(oof_node)
                                    .fixedpos_inline_container;
  }

  return NodeInfo(node, builder.ToConstraintSpace(), oof_static_position,
                  container_physical_content_size, container_info,
                  ConstraintSpace().GetWritingDirection(),
                  /* is_fragmentainer_descendant */ containing_block_fragment,
                  fixedpos_containing_block, fixedpos_inline_container,
                  oof_node.inline_container.container,
                  requires_content_before_breaking);
}

const NGLayoutResult* NGOutOfFlowLayoutPart::LayoutOOFNode(
    NodeToLayout& oof_node_to_layout,
    const LayoutBox* only_layout,
    const NGConstraintSpace* fragmentainer_constraint_space,
    bool is_known_to_be_last_fragmentainer) {
  const NodeInfo& node_info = oof_node_to_layout.node_info;
  OffsetInfo& offset_info = oof_node_to_layout.offset_info;
  if (offset_info.has_cached_layout_result) {
    DCHECK(offset_info.initial_layout_result);
    return offset_info.initial_layout_result;
  }

  NGBoxStrut scrollbars_before =
      ComputeScrollbarsForNonAnonymous(node_info.node);
  const NGLayoutResult* layout_result =
      Layout(oof_node_to_layout, fragmentainer_constraint_space,
             is_known_to_be_last_fragmentainer);
  NGBoxStrut scrollbars_after =
      ComputeScrollbarsForNonAnonymous(node_info.node);

  // Since out-of-flow positioning sets up a constraint space with fixed
  // inline-size, the regular layout code (|NGBlockNode::Layout()|) cannot
  // re-layout if it discovers that a scrollbar was added or removed. Handle
  // that situation here. The assumption is that if intrinsic logical widths are
  // dirty after layout, AND its inline-size depends on the intrinsic logical
  // widths, it means that scrollbars appeared or disappeared.
  if (node_info.node.GetLayoutBox()->IntrinsicLogicalWidthsDirty() &&
      offset_info.inline_size_depends_on_min_max_sizes) {
    WritingDirectionMode writing_mode_direction =
        node_info.node.Style().GetWritingDirection();
    bool freeze_horizontal = false, freeze_vertical = false;
    bool ignore_first_inline_freeze =
        scrollbars_after.InlineSum() && scrollbars_after.BlockSum();
    // If we're in a measure pass, freeze both scrollbars right away, to avoid
    // quadratic time complexity for deeply nested flexboxes.
    if (ConstraintSpace().CacheSlot() == NGCacheSlot::kMeasure) {
      freeze_horizontal = freeze_vertical = true;
      ignore_first_inline_freeze = false;
    }
    do {
      // Freeze any scrollbars that appeared, and relayout. Repeat until both
      // have appeared, or until the scrollbar situation doesn't change,
      // whichever comes first.
      AddScrollbarFreeze(scrollbars_before, scrollbars_after,
                         writing_mode_direction, &freeze_horizontal,
                         &freeze_vertical);
      if (ignore_first_inline_freeze) {
        ignore_first_inline_freeze = false;
        // We allow to remove the inline-direction scrollbar only once
        // because the box might have unnecessary scrollbar due to
        // SetIsFixedInlineSize(true).
        if (writing_mode_direction.IsHorizontal())
          freeze_horizontal = false;
        else
          freeze_vertical = false;
      }
      scrollbars_before = scrollbars_after;
      PaintLayerScrollableArea::FreezeScrollbarsRootScope freezer(
          *node_info.node.GetLayoutBox(), freeze_horizontal, freeze_vertical);

      if (!IsResumingLayout(oof_node_to_layout.break_token)) {
        // The offset itself does not need to be recalculated. However, the
        // |node_dimensions| and |initial_layout_result| may need to be updated,
        // so recompute the OffsetInfo.
        //
        // Only do this if we're currently building the first fragment of the
        // OOF. If we're resuming after a fragmentainer break, we can't update
        // our intrinsic inline-size. First of all, the intrinsic inline-size
        // should be the same across all fragments [1], and besides, this
        // operation would lead to performing a non-fragmented layout pass (to
        // measure intrinsic block-size; see IntrinsicBlockSizeFunc in
        // ComputeOutOfFlowBlockDimensions()), which in turn would overwrite the
        // result of the first fragment entry in LayoutBox without a break
        // token, causing major confusion everywhere.
        //
        // [1] https://drafts.csswg.org/css-break/#varying-size-boxes
        offset_info = CalculateOffset(node_info, only_layout,
                                      /* is_first_run */ false);
      }

      layout_result = Layout(oof_node_to_layout, fragmentainer_constraint_space,
                             is_known_to_be_last_fragmentainer);

      scrollbars_after = ComputeScrollbarsForNonAnonymous(node_info.node);
      DCHECK(!freeze_horizontal || !freeze_vertical ||
             scrollbars_after == scrollbars_before);
    } while (scrollbars_after != scrollbars_before);
  }

  return layout_result;
}

NGOutOfFlowLayoutPart::OffsetInfo NGOutOfFlowLayoutPart::CalculateOffset(
    const NodeInfo& node_info,
    const LayoutBox* only_layout,
    bool is_first_run,
    const NGLogicalAnchorQuery* stitched_anchor_query) {
  const ComputedStyle* style = &node_info.node.Style();

  // If `@position-fallback` exists, let |TryCalculateOffset| check if the
  // result fits.
  Element* element = nullptr;
  const ComputedStyle* next_fallback_style = nullptr;
  if (UNLIKELY(style->PositionFallback())) {
    DCHECK(RuntimeEnabledFeatures::CSSAnchorPositioningEnabled());
    element = DynamicTo<Element>(node_info.node.GetDOMNode());
    if (element) {
      if (const ComputedStyle* fallback_style =
              element->StyleForPositionFallback(0)) {
        style = fallback_style;
        next_fallback_style = element->StyleForPositionFallback(1);
      }
    }
  }

  wtf_size_t fallback_index = 1;
  while (true) {
    const bool test_if_margin_box_fits = next_fallback_style;
    OffsetInfo offset_info;
    if (TryCalculateOffset(node_info, *style, only_layout,
                           stitched_anchor_query, test_if_margin_box_fits,
                           is_first_run, &offset_info)) {
      return offset_info;
    }

    // If the result doesn't fit its containing block, try the next rule.
    DCHECK(next_fallback_style);
    style = next_fallback_style;
    DCHECK(element);
    next_fallback_style = element->StyleForPositionFallback(++fallback_index);
  }
}

bool NGOutOfFlowLayoutPart::TryCalculateOffset(
    const NodeInfo& node_info,
    const ComputedStyle& candidate_style,
    const LayoutBox* only_layout,
    const NGLogicalAnchorQuery* stitched_anchor_query,
    bool test_if_margin_box_fits,
    bool is_first_run,
    OffsetInfo* const offset_info) {
  const WritingDirectionMode candidate_writing_direction =
      candidate_style.GetWritingDirection();
  const auto container_writing_direction =
      node_info.container_info.writing_direction;
  const LogicalSize container_content_size_in_candidate_writing_mode =
      node_info.container_physical_content_size.ConvertToLogical(
          candidate_writing_direction.GetWritingMode());

  // Determine if we need to actually run the full OOF-positioned sizing, and
  // positioning algorithm.
  //
  // The first-tier cache compares the given available-size. However we can't
  // reuse the result if the |ContainingBlockInfo::container_offset| may change.
  // This can occur when:
  //  - The default containing-block has borders and/or scrollbars.
  //  - The candidate has an inline container (instead of the default
  //    containing-block).
  // Note: Only check for cache results if this is our first layout pass.
  if (is_first_run && !test_if_margin_box_fits && allow_first_tier_oof_cache_ &&
      !node_info.inline_container) {
    if (const NGLayoutResult* cached_result =
            node_info.node.CachedLayoutResultForOutOfFlowPositioned(
                container_content_size_in_candidate_writing_mode)) {
      offset_info->initial_layout_result = cached_result;
      offset_info->has_cached_layout_result = true;
      return true;
    }
  }

  absl::optional<NGAnchorEvaluatorImpl> anchor_evaluator_storage;
  const WritingModeConverter container_converter(
      container_writing_direction, node_info.container_physical_content_size);
  if (stitched_anchor_query) {
    // When the containing block is block-fragmented, the |container_builder_|
    // is the fragmentainer, not the containing block, and the coordinate system
    // is stitched. Use the given |anchor_query|.
    anchor_evaluator_storage.emplace(
        *stitched_anchor_query, container_converter,
        container_converter.ToPhysical(node_info.container_info.rect).offset,
        candidate_writing_direction.GetWritingMode());
  } else if (const NGLogicalAnchorQuery* anchor_query =
                 container_builder_->AnchorQuery()) {
    // Otherwise the |container_builder_| is the containing block.
    anchor_evaluator_storage.emplace(
        *anchor_query, container_converter,
        container_converter.ToPhysical(node_info.container_info.rect).offset,
        candidate_writing_direction.GetWritingMode());
  } else {
    anchor_evaluator_storage.emplace();
  }
  NGAnchorEvaluatorImpl* anchor_evaluator = &*anchor_evaluator_storage;

  const NGLogicalOutOfFlowInsets insets = ComputeOutOfFlowInsets(
      candidate_style, node_info.constraint_space.AvailableSize(),
      anchor_evaluator);

  const LogicalSize computed_available_size =
      ComputeOutOfFlowAvailableSize(node_info.node, node_info.constraint_space,
                                    insets, node_info.static_position);

  const NGBoxStrut border_padding =
      ComputeBorders(node_info.constraint_space, node_info.node) +
      ComputePadding(node_info.constraint_space, candidate_style);

  absl::optional<LogicalSize> replaced_size;
  if (node_info.node.IsReplaced()) {
    replaced_size = ComputeReplacedSize(
        node_info.node, node_info.constraint_space, border_padding,
        computed_available_size, ReplacedSizeMode::kNormal, anchor_evaluator);
  }

  NGLogicalOutOfFlowDimensions& node_dimensions = offset_info->node_dimensions;
  offset_info->inline_size_depends_on_min_max_sizes =
      ComputeOutOfFlowInlineDimensions(
          node_info.node, candidate_style, node_info.constraint_space, insets,
          border_padding, node_info.static_position, computed_available_size,
          replaced_size, container_writing_direction, anchor_evaluator,
          &node_dimensions);

  // Check if the inline dimension fits.
  const LogicalRect& container_rect = node_info.container_info.rect;
  const LogicalSize container_size_in_candidate_writing_mode =
      node_info.container_physical_content_size.ConvertToLogical(
          candidate_writing_direction.GetWritingMode());
  if (test_if_margin_box_fits) {
    if (node_dimensions.MarginBoxInlineStart() < 0 ||
        node_dimensions.MarginBoxInlineEnd() >
            container_size_in_candidate_writing_mode.inline_size) {
      return false;
    }
  }

  // We may have already pre-computed our block-dimensions when determining
  // our min/max sizes, only run if needed.
  if (node_dimensions.size.block_size == kIndefiniteSize) {
    offset_info->initial_layout_result = ComputeOutOfFlowBlockDimensions(
        node_info.node, candidate_style, node_info.constraint_space, insets,
        border_padding, node_info.static_position, computed_available_size,
        replaced_size, container_writing_direction, anchor_evaluator,
        &node_dimensions);
  }

  // Check if the block dimension fits.
  if (test_if_margin_box_fits) {
    if (node_dimensions.MarginBoxBlockStart() < 0 ||
        node_dimensions.MarginBoxBlockEnd() >
            container_size_in_candidate_writing_mode.block_size) {
      return false;
    }
  }

  offset_info->disable_first_tier_cache |=
      anchor_evaluator->HasAnchorFunctions();
  offset_info->block_estimate = node_dimensions.size.block_size;

  // Calculate the offsets.
  const NGBoxStrut inset =
      node_dimensions.inset.ConvertToPhysical(candidate_writing_direction)
          .ConvertToLogical(node_info.default_writing_direction);

  // |inset| is relative to the container's padding-box. Convert this to being
  // relative to the default container's border-box.
  offset_info->offset = container_rect.offset;
  offset_info->offset.inline_offset += inset.inline_start;
  offset_info->offset.block_offset += inset.block_start;

  if (!only_layout && !container_builder_->IsBlockFragmentationContextRoot()) {
    // OOFs contained by an inline that's been split into continuations are
    // special, as their offset is relative to a fragment that's not the same as
    // their containing NG fragment; take a look inside
    // AdjustOffsetForSplitInline() for further details. This doesn't apply if
    // block fragmentation is involved, though, since all OOFs are then child
    // fragments of the nearest fragmentainer.
    AdjustOffsetForSplitInline(node_info.node, container_builder_,
                               offset_info->offset);
  }

  return true;
}

const NGLayoutResult* NGOutOfFlowLayoutPart::Layout(
    const NodeToLayout& oof_node_to_layout,
    const NGConstraintSpace* fragmentainer_constraint_space,
    bool is_known_to_be_last_fragmentainer) {
  const NodeInfo& node_info = oof_node_to_layout.node_info;
  const WritingDirectionMode candidate_writing_direction =
      node_info.node.Style().GetWritingDirection();
  LogicalSize container_content_size_in_candidate_writing_mode =
      node_info.container_physical_content_size.ConvertToLogical(
          candidate_writing_direction.GetWritingMode());
  const OffsetInfo& offset_info = oof_node_to_layout.offset_info;
  LogicalOffset offset = offset_info.offset;

  // Reset the |layout_result| computed earlier to allow fragmentation in the
  // next layout pass, if needed.
  const NGLayoutResult* layout_result = !fragmentainer_constraint_space
                                            ? offset_info.initial_layout_result
                                            : nullptr;

  // Skip this step if we produced a fragment that can be reused when
  // estimating the block-size.
  if (!layout_result) {
    bool should_use_fixed_block_size = offset_info.block_estimate.has_value();

    // In some cases we will need the fragment size in order to calculate the
    // offset. We may have to lay out to get the fragment size. For block
    // fragmentation, we *need* to know the block-offset before layout. In other
    // words, in that case, we may have to lay out, calculate the offset, and
    // then lay out again at the correct block-offset.
    if (fragmentainer_constraint_space && offset_info.initial_layout_result)
      should_use_fixed_block_size = false;

    RepeatMode repeat_mode = kNotRepeated;
    if (container_builder_->Node().IsPaginatedRoot() &&
        node_info.node.Style().GetPosition() == EPosition::kFixed &&
        !oof_node_to_layout.containing_block_fragment) {
      // Fixed-positioned elements are repeated when paginated, if contained by
      // the initial containing block (i.e. when not contained by a transformed
      // element or similar).
      if (is_known_to_be_last_fragmentainer)
        repeat_mode = kRepeatedLast;
      else
        repeat_mode = kMayRepeatAgain;
    }

    layout_result = GenerateFragment(
        node_info.node, container_content_size_in_candidate_writing_mode,
        offset_info.block_estimate, offset_info.node_dimensions,
        offset.block_offset, oof_node_to_layout.break_token,
        fragmentainer_constraint_space, should_use_fixed_block_size,
        node_info.requires_content_before_breaking, repeat_mode);
  }

  if (layout_result->Status() != NGLayoutResult::kSuccess) {
    DCHECK_EQ(layout_result->Status(),
              NGLayoutResult::kOutOfFragmentainerSpace);
    return layout_result;
  }

  if (node_info.node.GetLayoutBox()->IsLayoutNGObject()) {
    To<LayoutBlock>(node_info.node.GetLayoutBox())
        ->SetIsLegacyInitiatedOutOfFlowLayout(false);
  }
  // Legacy grid and flexbox handle OOF-positioned margins on their own, and
  // break if we set them here.
  if (!container_builder_->GetLayoutObject()
           ->Style()
           ->IsDisplayFlexibleOrGridBox()) {
    node_info.node.GetLayoutBox()->SetMargin(
        offset_info.node_dimensions.margins.ConvertToPhysical(
            candidate_writing_direction));
  }

  layout_result->GetMutableForOutOfFlow().SetOutOfFlowPositionedOffset(
      offset,
      allow_first_tier_oof_cache_ && !offset_info.disable_first_tier_cache);

  return layout_result;
}

bool NGOutOfFlowLayoutPart::IsContainingBlockForCandidate(
    const NGLogicalOutOfFlowPositionedNode& candidate) {
  // Fragmentainers are not allowed to be containing blocks.
  if (container_builder_->IsFragmentainerBoxType())
    return false;

  EPosition position = candidate.Node().Style().GetPosition();

  // Candidates whose containing block is inline are always positioned inside
  // closest parent block flow.
  if (candidate.inline_container.container) {
    DCHECK(candidate.inline_container.container
               ->CanContainOutOfFlowPositionedElement(position));
    return container_builder_->GetLayoutObject() ==
           candidate.box->ContainingBlock();
  }
  return (is_absolute_container_ && position == EPosition::kAbsolute) ||
         (is_fixed_container_ && position == EPosition::kFixed);
}

// The fragment is generated in one of these two scenarios:
// 1. To estimate candidate's block size, in this case block_size is
//    container's available size.
// 2. To compute final fragment, when block size is known from the absolute
//    position calculation.
const NGLayoutResult* NGOutOfFlowLayoutPart::GenerateFragment(
    NGBlockNode node,
    const LogicalSize& container_content_size_in_candidate_writing_mode,
    const absl::optional<LayoutUnit>& block_estimate,
    const NGLogicalOutOfFlowDimensions& node_dimensions,
    const LayoutUnit block_offset,
    const NGBlockBreakToken* break_token,
    const NGConstraintSpace* fragmentainer_constraint_space,
    bool should_use_fixed_block_size,
    bool requires_content_before_breaking,
    RepeatMode repeat_mode) {
  const auto& style = node.Style();

  LayoutUnit inline_size = node_dimensions.size.inline_size;
  LayoutUnit block_size = block_estimate.value_or(
      container_content_size_in_candidate_writing_mode.block_size);
  LogicalSize logical_size(inline_size, block_size);
  // Convert from logical size in the writing mode of the child to the logical
  // size in the writing mode of the container. That's what the constraint space
  // builder expects.
  PhysicalSize physical_size =
      ToPhysicalSize(logical_size, style.GetWritingMode());
  LogicalSize available_size =
      physical_size.ConvertToLogical(ConstraintSpace().GetWritingMode());
  bool is_repeatable = false;

  NGConstraintSpaceBuilder builder(ConstraintSpace(),
                                   style.GetWritingDirection(),
                                   /* is_new_fc */ true);
  builder.SetAvailableSize(available_size);
  builder.SetPercentageResolutionSize(
      container_content_size_in_candidate_writing_mode);
  builder.SetIsFixedInlineSize(true);
  if (should_use_fixed_block_size)
    builder.SetIsFixedBlockSize(true);
  if (fragmentainer_constraint_space) {
    if (repeat_mode != kNotRepeated) {
      // Paginated fixed-positioned elements are repeated on every page, and may
      // therefore not fragment.
      DCHECK(container_builder_->Node().IsPaginatedRoot());
      DCHECK_EQ(node.Style().GetPosition(), EPosition::kFixed);
      builder.SetShouldRepeat(repeat_mode != kRepeatedLast);
      builder.SetIsInsideRepeatableContent(true);
      is_repeatable = true;
    } else {
      SetupSpaceBuilderForFragmentation(
          *fragmentainer_constraint_space, node, block_offset, &builder,
          /* is_new_fc */ true, requires_content_before_breaking);
    }
  } else if (container_builder_->IsInitialColumnBalancingPass()) {
    SetupSpaceBuilderForFragmentation(
        ConstraintSpace(), node, block_offset, &builder, /* is_new_fc */ true,
        /* requires_content_before_breaking */ false);
  }
  DeferredShapingMinimumTopScope minimum_top_scope(node, block_offset);
  NGConstraintSpace space = builder.ToConstraintSpace();

  if (is_repeatable)
    return node.LayoutRepeatableRoot(space, break_token);
  return node.Layout(space, break_token);
}

void NGOutOfFlowLayoutPart::LayoutOOFsInFragmentainer(
    HeapVector<NodeToLayout>& pending_descendants,
    wtf_size_t index,
    LogicalOffset fragmentainer_progression,
    bool is_last_fragmentainer_with_oof_descendants,
    HeapVector<NodeToLayout>* fragmented_descendants) {
  auto& children = FragmentationContextChildren();
  wtf_size_t num_children = children.size();
  bool is_new_fragment = index >= num_children;
  bool is_known_to_have_more_fragmentainers =
      index + 1 < num_children || !is_last_fragmentainer_with_oof_descendants;

  DCHECK(fragmented_descendants);
  HeapVector<NodeToLayout> descendants_continued;
  ClearCollectionScope<HeapVector<NodeToLayout>> descendants_continued_scope(
      &descendants_continued);
  std::swap(*fragmented_descendants, descendants_continued);

  // If |index| is greater than the number of current children, and there are
  // no OOF children to be added, we will still need to add an empty
  // fragmentainer in its place. Otherwise, return early since there is no work
  // to do.
  if (pending_descendants.empty() && descendants_continued.empty() &&
      !is_new_fragment)
    return;

  const NGConstraintSpace& space = GetFragmentainerConstraintSpace(index);

  // If we are a new fragment, find a non-spanner fragmentainer as a basis.
  wtf_size_t original_index = index;
  while (index >= num_children ||
         !children[index].fragment->IsFragmentainerBox()) {
    DCHECK_GT(num_children, 0u);
    index--;
  }

  const auto& fragmentainer = children[index];
  DCHECK(fragmentainer.fragment->IsFragmentainerBox());
  const NGBlockNode& node = container_builder_->Node();
  const auto* fragment =
      To<NGPhysicalBoxFragment>(fragmentainer.fragment.Get());
  NGFragmentGeometry fragment_geometry =
      CalculateInitialFragmentGeometry(space, node, /* break_token */ nullptr);
  LogicalOffset fragmentainer_offset = UpdatedFragmentainerOffset(
      fragmentainer.offset, index, fragmentainer_progression, is_new_fragment);

  const NGBlockBreakToken* previous_break_token = nullptr;
  if (!column_balancing_info_) {
    // Note: We don't fetch this when column balancing because we don't actually
    // create and add new fragments to the builder until a later layout pass.
    // However, the break token is only needed when we are actually adding to
    // the builder, so it is ok to leave this as nullptr in such cases.
    previous_break_token =
        PreviousFragmentainerBreakToken(*container_builder_, original_index);
  }
  NGLayoutAlgorithmParams params(node, fragment_geometry, space,
                                 previous_break_token,
                                 /* early_break */ nullptr);

  bool is_known_to_be_last_fragmentainer = false;

  do {
    // |algorithm| corresponds to the "mutable copy" of our original
    // fragmentainer. As long as this "copy" hasn't been laid out via
    // NGSimplifiedOOFLayoutAlgorithm::Layout, we can append new items to it.
    NGSimplifiedOOFLayoutAlgorithm algorithm(params, *fragment,
                                             is_new_fragment);
    // Layout any OOF elements that are a continuation of layout first.
    for (auto& descendant : descendants_continued) {
      AddOOFToFragmentainer(descendant, &space, fragmentainer_offset, index,
                            is_known_to_be_last_fragmentainer, &algorithm,
                            fragmented_descendants);
    }
    // Once we've laid out the OOF elements that are a continuation of layout,
    // we can layout the OOF elements that start layout in the current
    // fragmentainer.
    for (auto& descendant : pending_descendants) {
      AddOOFToFragmentainer(descendant, &space, fragmentainer_offset, index,
                            is_known_to_be_last_fragmentainer, &algorithm,
                            fragmented_descendants);
    }

    if (container_builder_->Node().IsPaginatedRoot() &&
        !is_known_to_have_more_fragmentainers &&
        !fragmented_descendants->empty()) {
      // This will be the last fragmentainer, unless we have regular
      // (i.e. non-repeated) out-of-flow positioned elements that fragmented.
      bool has_descendant_with_break = false;
      for (const auto& descendant : *fragmented_descendants) {
        DCHECK(descendant.break_token);
        if (!descendant.break_token->IsRepeated()) {
          has_descendant_with_break = true;
          break;
        }
      }
      if (!has_descendant_with_break) {
        // This turned out to be the last fragmentainer. We didn't know that
        // up-front, so that all repeated fixed positioned fragments created a
        // repeat break token. But they are not going to repeat any further, so
        // we now need a re-layout with that in mind (so that they don't get
        // outgoing break tokens).
        is_known_to_be_last_fragmentainer = true;
        fragmented_descendants->clear();
        continue;
      }
    }
    // Finalize layout on the cloned fragmentainer and replace all existing
    // references to the old result.
    ReplaceFragmentainer(index, fragmentainer_offset, is_new_fragment,
                         &algorithm);
    break;
  } while (true);
}

void NGOutOfFlowLayoutPart::AddOOFToFragmentainer(
    NodeToLayout& descendant,
    const NGConstraintSpace* fragmentainer_space,
    LogicalOffset fragmentainer_offset,
    wtf_size_t index,
    bool is_known_to_be_last_fragmentainer,
    NGSimplifiedOOFLayoutAlgorithm* algorithm,
    HeapVector<NodeToLayout>* fragmented_descendants) {
  const NGLayoutResult* result =
      LayoutOOFNode(descendant, /* only_layout */ nullptr, fragmentainer_space,
                    is_known_to_be_last_fragmentainer);

  if (result->Status() != NGLayoutResult::kSuccess) {
    DCHECK_EQ(result->Status(), NGLayoutResult::kOutOfFragmentainerSpace);
    // If we're out of space, continue layout in the next fragmentainer.
    NodeToLayout fragmented_descendant = descendant;
    fragmented_descendant.offset_info.offset.block_offset = LayoutUnit();
    fragmented_descendants->emplace_back(fragmented_descendant);
    return;
  }

  // Apply the relative positioned offset now that fragmentation is complete.
  LogicalOffset oof_offset = result->OutOfFlowPositionedOffset();
  LogicalOffset relative_offset =
      descendant.node_info.container_info.relative_offset;
  LogicalOffset adjusted_offset = oof_offset + relative_offset;

  // In the case where an OOF descendant of |descendant| has its containing
  // block outside the current fragmentation context, we will want to apply an
  // additional offset to |oof_offset| in PropagateOOFPositionedInfo() such that
  // it's the offset relative to the current builder rather than the offset such
  // that all fragmentainers are stacked on top of each other.
  LogicalOffset offset_adjustment = fragmentainer_offset;

  result->GetMutableForOutOfFlow().SetOutOfFlowPositionedOffset(
      adjusted_offset, allow_first_tier_oof_cache_);

  LogicalOffset additional_fixedpos_offset;
  if (descendant.node_info.fixedpos_containing_block.Fragment()) {
    additional_fixedpos_offset =
        descendant.offset_info.original_offset -
        descendant.node_info.fixedpos_containing_block.Offset();
    // Currently, |additional_fixedpos_offset| is the offset from the top of
    // |descendant| to the fixedpos containing block. Adjust this so that it
    // includes the block contribution of |descendant| from previous
    // fragmentainers. This ensures that any fixedpos descendants in the current
    // fragmentainer have the correct static position.
    if (descendant.break_token) {
      additional_fixedpos_offset.block_offset +=
          descendant.break_token->ConsumedBlockSize();
    }
  } else if (outer_context_has_fixedpos_container_) {
    // If the fixedpos containing block is in an outer fragmentation context,
    // we should adjust any fixedpos static positions such that they are
    // relative to the top of the inner multicol. These will eventually be
    // updated again with the offset from the multicol to the fixedpos
    // containing block such that the static positions are relative to the
    // containing block.
    DCHECK(multicol_children_);
    for (wtf_size_t i = std::min(index, multicol_children_->size()); i > 0u;
         i--) {
      MulticolChildInfo& column_info = (*multicol_children_)[i - 1];
      if (column_info.parent_break_token) {
        additional_fixedpos_offset.block_offset +=
            column_info.parent_break_token->ConsumedBlockSize();
        break;
      }
    }
  }

  const auto& physical_fragment =
      To<NGPhysicalBoxFragment>(result->PhysicalFragment());
  const NGBlockBreakToken* break_token = physical_fragment.BreakToken();
  if (break_token) {
    DCHECK(!is_known_to_be_last_fragmentainer);
    // We must continue layout in the next fragmentainer. Update any information
    // in NodeToLayout, and add the node to |fragmented_descendants|.
    NodeToLayout fragmented_descendant = descendant;
    fragmented_descendant.break_token = break_token;
    if (!break_token->IsRepeated())
      fragmented_descendant.offset_info.offset.block_offset = LayoutUnit();
    fragmented_descendants->emplace_back(fragmented_descendant);
  }

  // Figure out if the current OOF affects column balancing. Then return since
  // we don't want to add the OOFs to the builder until the current columns have
  // completed layout.
  if (column_balancing_info_) {
    LayoutUnit space_shortage = CalculateSpaceShortage(
        *fragmentainer_space, result, oof_offset.block_offset);
    column_balancing_info_->PropagateSpaceShortage(space_shortage);
    // We don't check the break appeal of the layout result to determine if
    // there is a violating break because OOFs aren't affected by the various
    // break rules. However, OOFs aren't pushed to the next fragmentainer if
    // they don't fit (when they are monolithic). Use |has_violating_break| to
    // tell the column algorithm when this happens so that it knows to attempt
    // to expand the columns in such cases.
    if (!column_balancing_info_->has_violating_break) {
      if (space_shortage > LayoutUnit() && !physical_fragment.BreakToken())
        column_balancing_info_->has_violating_break = true;
    }
    return;
  }

  // Propagate new data to the |container_builder_|. |AppendOutOfFlowResult|
  // will add the |result| to the fragmentainer, and replace the fragmentainer
  // in the |container_builder_|. |ReplaceChild| can't compute the differences
  // of the new and the old fragments, so it skips all propagations usually done
  // in |AddChild|.
  container_builder_->PropagateChildAnchors(
      physical_fragment, oof_offset + relative_offset + offset_adjustment);
  LayoutUnit containing_block_adjustment =
      container_builder_->BlockOffsetAdjustmentForFragmentainer(
          fragmentainer_consumed_block_size_);
  if (result->PhysicalFragment().NeedsOOFPositionedInfoPropagation()) {
    container_builder_->PropagateOOFPositionedInfo(
        result->PhysicalFragment(), oof_offset, relative_offset,
        offset_adjustment,
        /* inline_container */ nullptr, containing_block_adjustment,
        &descendant.node_info.fixedpos_containing_block,
        &descendant.node_info.fixedpos_inline_container,
        additional_fixedpos_offset);
  }
  algorithm->AppendOutOfFlowResult(result);

  // Copy the offset of the OOF node back to legacy such that it is relative
  // to its containing block rather than the fragmentainer that it is being
  // added to.
  if (!descendant.break_token) {
    const NGPhysicalBoxFragment* container =
        To<NGPhysicalBoxFragment>(descendant.containing_block_fragment.Get());

    if (!container) {
      // If we're paginated, we don't have a containing block fragment, but we
      // need one now, to calcualte the position correctly for the legacy
      // engine. Just pick the first page, which actually happens to be defined
      // as the initial containing block:
      // https://www.w3.org/TR/CSS22/page.html#page-box
      DCHECK(container_builder_->Node().IsPaginatedRoot());
      container = To<NGPhysicalBoxFragment>(
          FragmentationContextChildren()[0].fragment.Get());
    }

    LogicalOffset legacy_offset =
        descendant.offset_info.original_offset -
        descendant.node_info.container_info.offset_to_border_box;
    descendant.node_info.node.CopyChildFragmentPosition(
        physical_fragment,
        legacy_offset.ConvertToPhysical(
            container->Style().GetWritingDirection(), container->Size(),
            physical_fragment.Size()),
        *container, /* previous_container_break_token */ nullptr);
  }
}

void NGOutOfFlowLayoutPart::ReplaceFragmentainer(
    wtf_size_t index,
    LogicalOffset offset,
    bool create_new_fragment,
    NGSimplifiedOOFLayoutAlgorithm* algorithm) {
  // Don't update the builder when performing column balancing.
  if (column_balancing_info_)
    return;

  if (create_new_fragment) {
    const NGLayoutResult* new_result = algorithm->Layout();
    container_builder_->AddChild(
        new_result->PhysicalFragment(), offset,
        /* margin_strut */ nullptr, /* is_self_collapsing */ false,
        /* relative_offset */ absl::nullopt,
        /* inline_container */ nullptr,
        /* adjustment_for_oof_propagation */ absl::nullopt);
  } else {
    const NGLayoutResult* new_result = algorithm->Layout();
    const NGPhysicalFragment* new_fragment = &new_result->PhysicalFragment();
    container_builder_->ReplaceChild(index, *new_fragment, offset);

    if (multicol_children_ && index < multicol_children_->size()) {
      // We are in a nested fragmentation context. Replace the column entry
      // (that already existed) directly in the existing multicol fragment. If
      // there any new columns, they will be appended as part of regenerating
      // the multicol fragment.
      MulticolChildInfo& column_info = (*multicol_children_)[index];
      column_info.mutable_link->fragment = new_fragment;
    }
  }
}

LogicalOffset NGOutOfFlowLayoutPart::UpdatedFragmentainerOffset(
    LogicalOffset offset,
    wtf_size_t index,
    LogicalOffset fragmentainer_progression,
    bool create_new_fragment) {
  if (create_new_fragment) {
    auto& children = FragmentationContextChildren();
    wtf_size_t num_children = children.size();
    if (index != num_children - 1 &&
        !children[index + 1].fragment->IsFragmentainerBox()) {
      // If we are a new fragment and are separated from other columns by a
      // spanner, compute the correct column offset to use.
      const auto& spanner = children[index + 1];
      DCHECK(spanner.fragment->IsColumnSpanAll());

      offset = spanner.offset;
      LogicalSize spanner_size = spanner.fragment->Size().ConvertToLogical(
          container_builder_->Style().GetWritingMode());
      // TODO(almaher): Include trailing spanner margin.
      offset.block_offset += spanner_size.block_size;
    } else {
      offset += fragmentainer_progression;
    }
  }
  return offset;
}

NGConstraintSpace NGOutOfFlowLayoutPart::GetFragmentainerConstraintSpace(
    wtf_size_t index) {
  auto& children = FragmentationContextChildren();
  wtf_size_t num_children = children.size();
  bool is_new_fragment = index >= num_children;
  // Allow margins to be discarded if this is not the first column in the
  // multicol container, and we're not right after a spanner.
  //
  // TODO(layout-dev): This check is incorrect in nested multicol. If the
  // previous outer fragmentainer ended with regular column content (i.e. not a
  // spanner), and this is the first column in the next outer fragmentainer, we
  // should still discard margins, since there is no explicit break involved.
  bool allow_discard_start_margin =
      is_new_fragment ||
      (index > 0 && children[index - 1].fragment->IsFragmentainerBox());

  // If we are a new fragment, find a non-spanner fragmentainer to base our
  // constraint space off of.
  while (index >= num_children ||
         !children[index].fragment->IsFragmentainerBox()) {
    DCHECK_GT(num_children, 0u);
    index--;
  }

  const auto& fragmentainer = children[index];
  DCHECK(fragmentainer.fragment->IsFragmentainerBox());
  const auto& fragment = To<NGPhysicalBoxFragment>(*fragmentainer.fragment);
  const WritingMode container_writing_mode =
      container_builder_->Style().GetWritingMode();
  LogicalSize column_size =
      fragment.Size().ConvertToLogical(container_writing_mode);

  // If we are a new fragment and are separated from other columns by a
  // spanner, compute the correct column block size to use.
  if (is_new_fragment && index != num_children - 1 &&
      original_column_block_size_ != kIndefiniteSize &&
      !children[index + 1].fragment->IsFragmentainerBox()) {
    column_size.block_size =
        original_column_block_size_ -
        container_builder_->BlockOffsetForAdditionalColumns();
    column_size.block_size = column_size.block_size.ClampNegativeToZero();
  }

  LogicalSize percentage_resolution_size =
      LogicalSize(column_size.inline_size,
                  container_builder_->ChildAvailableSize().block_size);

  // In the current implementation it doesn't make sense to restrict imperfect
  // breaks inside OOFs, since we never break and resume OOFs in a subsequent
  // outer fragmentainer anyway (we'll always stay in the current outer
  // fragmentainer and just create overflowing columns in the current row,
  // rather than moving to the next one).
  NGBreakAppeal min_break_appeal = kBreakAppealLastResort;

  // TODO(bebeaudr): Need to handle different fragmentation types. It won't
  // always be multi-column.
  return CreateConstraintSpaceForColumns(
      ConstraintSpace(), column_size, percentage_resolution_size,
      allow_discard_start_margin, /* balance_columns */ false,
      min_break_appeal);
}

// Compute in which fragmentainer the OOF element will start its layout and
// position the offset relative to that fragmentainer.
void NGOutOfFlowLayoutPart::ComputeStartFragmentIndexAndRelativeOffset(
    WritingMode default_writing_mode,
    LayoutUnit block_estimate,
    wtf_size_t* start_index,
    LogicalOffset* offset) const {
  wtf_size_t child_index = 0;
  // The sum of all previous fragmentainers' block size.
  LayoutUnit used_block_size;
  // The sum of all previous fragmentainers' block size + the current one.
  LayoutUnit current_max_block_size;
  // The block size for the last fragmentainer we encountered.
  LayoutUnit fragmentainer_block_size;
  auto& children = FragmentationContextChildren();
  // TODO(bebeaudr): There is a possible performance improvement here as we'll
  // repeat this for each abspos in a same fragmentainer.
  for (auto& child : children) {
    if (child.fragment->IsFragmentainerBox()) {
      fragmentainer_block_size = child.fragment->Size()
                                     .ConvertToLogical(default_writing_mode)
                                     .block_size;
      fragmentainer_block_size =
          ClampedToValidFragmentainerCapacity(fragmentainer_block_size);
      current_max_block_size += fragmentainer_block_size;

      // Edge case: an abspos with an height of 0 positioned exactly at the
      // |current_max_block_size| won't be fragmented, so no break token will be
      // produced - as we'd expect. However, the break token is used to compute
      // the |fragmentainer_consumed_block_size_| stored on the
      // |container_builder_| when we have a nested abspos. Because we use that
      // value to position the nested abspos, its start offset would be off by
      // exactly one fragmentainer block size.
      if (offset->block_offset < current_max_block_size ||
          (offset->block_offset == current_max_block_size &&
           block_estimate == 0)) {
        *start_index = child_index;
        offset->block_offset -= used_block_size;
        return;
      }
      used_block_size = current_max_block_size;
    }
    child_index++;
  }
  // If the right fragmentainer hasn't been found yet, the OOF element will
  // start its layout in a proxy fragment.
  LayoutUnit remaining_block_offset = offset->block_offset - used_block_size;

  // If we are a new fragment and are separated from other columns by a
  // spanner, compute the correct fragmentainer_block_size.
  if (original_column_block_size_ != kIndefiniteSize &&
      !children[child_index - 1].fragment->IsFragmentainerBox()) {
    fragmentainer_block_size =
        original_column_block_size_ -
        container_builder_->BlockOffsetForAdditionalColumns();
    fragmentainer_block_size =
        ClampedToValidFragmentainerCapacity(fragmentainer_block_size);
  }

  wtf_size_t additional_fragment_count =
      int(floorf(remaining_block_offset / fragmentainer_block_size));
  *start_index = child_index + additional_fragment_count;
  offset->block_offset = remaining_block_offset -
                         additional_fragment_count * fragmentainer_block_size;
}

void NGOutOfFlowLayoutPart::ReplaceFragment(
    const NGLayoutResult* new_result,
    const NGPhysicalBoxFragment& old_fragment,
    wtf_size_t index) {
  // Replace the LayoutBox entry.
  LayoutBox& box = *old_fragment.MutableOwnerLayoutBox();
  box.ReplaceLayoutResult(new_result, index);

  // Replace the entry in the parent fragment. Locating the parent fragment
  // isn't straight-forward if the containing block is a multicol container.
  LayoutBlock* containing_block = box.ContainingNGBlock();

  if (box.IsOutOfFlowPositioned()) {
    // If the inner multicol is out-of-flow positioned, its fragments will be
    // found as direct children of fragmentainers in some ancestor fragmentation
    // context. It may not be the *nearest* fragmentation context, though, since
    // the OOF inner multicol may be contained by other OOFs, which in turn may
    // not be contained by the innermost multicol container, and so on. Skip
    // above all OOFs in the containing block chain, to find the right
    // fragmentation context root.
    while (containing_block->IsOutOfFlowPositioned() &&
           !containing_block->IsLayoutView())
      containing_block = containing_block->ContainingNGBlock();
    // If we got to the root LayoutView, it has to mean that it establishes a
    // fragmentation context (i.e. we're printing).
    if (containing_block->IsLayoutView())
      DCHECK(containing_block->IsFragmentationContextRoot());
    else
      containing_block = containing_block->ContainingFragmentationContextRoot();

    // Since this is treated as a nested multicol container, we should always
    // find an outer fragmentation context.
    DCHECK(containing_block);
  }

  // Replace the old fragment with the new one, if it's inside |parent|.
  auto ReplaceChild = [&new_result, &old_fragment](
                          const NGPhysicalBoxFragment& parent) -> bool {
    for (NGLink& child_link :
         parent.GetMutableChildrenForOutOfFlow().Children()) {
      if (child_link.fragment != &old_fragment)
        continue;
      child_link.fragment = &new_result->PhysicalFragment();
      return true;
    }
    return false;
  };

  // Replace the old fragment with the new one, if |multicol_child| is a
  // fragmentainer and has the old fragment as a child.
  auto ReplaceFragmentainerChild =
      [ReplaceChild](const NGPhysicalFragment& multicol_child) -> bool {
    // We're going to replace a child of a fragmentainer. First check if it's a
    // fragmentainer at all.
    if (!multicol_child.IsFragmentainerBox())
      return false;
    const auto& fragmentainer = To<NGPhysicalBoxFragment>(multicol_child);
    // Then search and replace inside the fragmentainer.
    return ReplaceChild(fragmentainer);
  };

  if (!containing_block->IsFragmentationContextRoot()) {
    DCHECK_NE(containing_block, container_builder_->GetLayoutObject());
    DCHECK(!box.IsColumnSpanAll());
    for (const auto& parent_fragment : containing_block->PhysicalFragments()) {
      if (parent_fragment.HasItems()) {
        // Look inside the inline formatting context to find and replace the
        // fragment generated for the nested multicol container. This happens
        // when we have a floated "inline-level" nested multicol container with
        // an OOF inside.
        if (NGFragmentItems::ReplaceBoxFragment(
                old_fragment,
                To<NGPhysicalBoxFragment>(new_result->PhysicalFragment()),
                parent_fragment))
          return;
      }
      // Search inside child fragments of the containing block.
      if (ReplaceChild(parent_fragment))
        return;
    }
  } else if (containing_block == container_builder_->GetLayoutObject()) {
    DCHECK(!box.IsColumnSpanAll());
    // We're currently laying out |containing_block|, and it's a multicol
    // container. Search inside fragmentainer children in the builder.
    auto& children = FragmentationContextChildren();
    for (const NGLogicalLink& child : children) {
      if (ReplaceFragmentainerChild(*child.fragment))
        return;
    }
  } else {
    // |containing_block| has already been laid out, and it's a multicol
    // container. Search inside fragmentainer children of the fragments
    // generated for the containing block.
    for (const auto& multicol : containing_block->PhysicalFragments()) {
      if (box.IsColumnSpanAll()) {
        // Column spanners are found as direct children of the multicol.
        if (ReplaceChild(multicol))
          return;
      } else {
        for (const auto& child : multicol.Children()) {
          if (ReplaceFragmentainerChild(*child.fragment))
            return;
        }
      }
    }
  }
  NOTREACHED();
}

void NGOutOfFlowLayoutPart::SaveStaticPositionOnPaintLayer(
    LayoutBox* layout_box,
    const NGLogicalStaticPosition& position) const {
  const LayoutObject* parent =
      GetLayoutObjectForParentNode<const LayoutObject*>(layout_box);
  const LayoutObject* container = container_builder_->GetLayoutObject();
  if (parent == container ||
      (parent->IsLayoutInline() && parent->ContainingBlock() == container)) {
    DCHECK(layout_box->Layer());
    layout_box->Layer()->SetStaticPositionFromNG(
        ToStaticPositionForLegacy(position));
  }
}

NGLogicalStaticPosition NGOutOfFlowLayoutPart::ToStaticPositionForLegacy(
    NGLogicalStaticPosition position) const {
  // Legacy expects the static position to include the block contribution from
  // previous columns.
  if (const auto* break_token = container_builder_->PreviousBreakToken())
    position.offset.block_offset += break_token->ConsumedBlockSizeForLegacy();
  return position;
}

void NGOutOfFlowLayoutPart::ColumnBalancingInfo::PropagateSpaceShortage(
    LayoutUnit space_shortage) {
  UpdateMinimalSpaceShortage(space_shortage, &minimal_space_shortage);
}

void NGOutOfFlowLayoutPart::MulticolChildInfo::Trace(Visitor* visitor) const {
  visitor->Trace(parent_break_token);
}

void NGOutOfFlowLayoutPart::NodeInfo::Trace(Visitor* visitor) const {
  visitor->Trace(node);
  visitor->Trace(fixedpos_containing_block);
  visitor->Trace(fixedpos_inline_container);
}

void NGOutOfFlowLayoutPart::OffsetInfo::Trace(Visitor* visitor) const {
  visitor->Trace(initial_layout_result);
}

void NGOutOfFlowLayoutPart::NodeToLayout::Trace(Visitor* visitor) const {
  visitor->Trace(node_info);
  visitor->Trace(offset_info);
  visitor->Trace(break_token);
  visitor->Trace(containing_block_fragment);
}

}  // namespace blink
