// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/**
 * @fileoverview Interface used to extract the page text, up to a certain limit,
 * and pass it on to the annotations manager.
 */

import {gCrWeb} from '//ios/web/public/js_messaging/resources/gcrweb.js';
import {sendWebKitMessage} from '//ios/web/public/js_messaging/resources/utils.js'

// Mark: Debug

// TODO(crbug.com/1350973): remove on full launch.
function log(value: any) {
  sendWebKitMessage('annotations', {
    command: 'annotations.log',
    text: gCrWeb.stringify(value),
  });
}

// Mark: Private properties

interface Annotation {
  /**  Character index to start of annotation. */
  start: number;
  /** Character index to end of annotation (first character after text) */
  end: number;
  /** The annotation text used to ensure the text in the page hasn't changed. */
  text: string;
  /** Annotation type. */
  type: string;
  /** A passed in string that will be sent back to obj in tap handler. */
  data: string;
}

/**
 * `Replacement` represent a single child node replacement inside a childless
 * text Node. `index` is the source annotation index. `left` and `right` is the
 * range inside `text` for a child Node.
 * All new nodes (HTMLElement) have `data` added to their `dataset`.
 */
class Replacement {
  constructor(
      public index: number, public left: number, public right: number,
      public text: string, public type: string, public annotationText: string,
      public data: string) {}
}

/**
 * A `Decoration` is the `original` Node and the list of `Node`s that make up
 * a similar replacement.
 */
class Decoration {
  constructor(public original: Node, public replacements: Node[]) {}
}

/**
 * Section (like find in page) is used to be able to find text even if
 * there are DOM changes between extraction and decoration. Using WeakRef
 * around nodes also avoids holding on to deleted nodes.
 * TODO(crbug.com/1350973): WeakRef starts in 14.5, remove checks once 14 is
 *   deprecated. This also means that < 14.5 sectionsNodes is never releasing
 *   nodes, even if they are released from the DOM.
 */
class Section {
  constructor(public node: Node|WeakRef<Node>, public index: number) {}
}

/**
 * Monitors DOM mutations between instance construction until a call to
 * `stopObserving`.
 */
class MutationsDuringClickTracker {
  mutationCount = 0;
  mutationObserver: MutationObserver;

  // Constructs a new instance given an `initialEvent` and starts listening for
  // changes to the DOM.
  constructor(private readonly initialEvent: Event) {
    this.mutationObserver =
        new MutationObserver((mutationList: MutationRecord[]) => {
          this.mutationCount += mutationList.length;
        });
    this.mutationObserver.observe(
        document, {attributes: false, childList: true, subtree: true});
  }

  // Returns true if event matches the event passed at construction, it wasn't
  // prevented and no DOM mutations occurred.
  hasPreventativeActivity(event: Event): boolean {
    return event !== this.initialEvent || event.defaultPrevented ||
        this.mutationCount > 0;
  }

  stopObserving(): void {
    this.mutationObserver?.disconnect();
  }
}

// Used by the `enumerateTextNodes` function below.
const NON_TEXT_NODE_NAMES = new Set([
  'SCRIPT', 'NOSCRIPT', 'STYLE', 'EMBED', 'OBJECT', 'TEXTAREA', 'IFRAME',
  'INPUT'
]);

const highlightTextColor = "#000";
const highlightBackgroundColor = "rgba(20,111,225,0.25)";
const decorationStyles = 'border-bottom-width: 1px; ' +
    'border-bottom-style: dotted; ' +
    'background-color: transparent';
const decorationDefaultColor = 'blue';

/**
 * Callback for processing a node during DOM traversal
 * @param node - Node or Element containing text or newline.
 * @param index - index into the stream or characters so far.
 * @param text - the text for this node.
 */
type EnumNodesFunction = (node: Node, index: number, text: string) => boolean;

let decorations: Decoration[];

let sections: Section[];

// Mark: Public API functions called from native code.

/**
 * Extracts first `maxChars` text characters from the page. Once done it
 * send a 'annotations.extractedText' command with the 'text'.
 * @param maxChars - maximum number of characters to parse out.
 */
function extractText(maxChars: number): void {
  sendWebKitMessage('annotations', {
    command: 'annotations.extractedText',
    text: getPageText(maxChars),
  });
}

/**
 * Decorate the page with the given `annotations`. Annotations will be sorted
 * and one will be dropped when two overlaps.
 * @param annotations - list of annotations.
 */
function decorateAnnotations(annotations: Annotation[]): void {
  // Avoid redoing without going through `removeDecorations` first.
  if (decorations?.length || !annotations.length)
    return;

  let failures = 0;
  decorations = [];

  // Last checks when bubbling up event.
  document.addEventListener('click', handleTopTap.bind(document));

  removeOverlappingAnnotations(annotations);

  // Reparse page finding annotations and styling them.
  let annotationIndex = 0;
  enumerateSectionsNodes((node, index, text) => {
    if (!node.parentNode)
      return true;

    // Skip annotation with end before index. This would happen if some nodes
    // are deleted between text fetching and decorating.
    while (annotationIndex < annotations.length) {
      const annotation = annotations[annotationIndex];
      if (!annotation || annotation.end > index) {
        break;
      }
      log({
        reason: 'skipping',
        annotationText: annotation.text,
      });
      failures++;
      annotationIndex++;
    }

    const length = text.length;
    let replacements: Replacement[] = [];
    while (annotationIndex < annotations.length) {
      const annotation = annotations[annotationIndex];
      if (!annotation) {
        break;
      }
      const start = annotation.start;
      const end = annotation.end;
      if (index < end && index + length > start) {
        // Inside node
        const left = Math.max(0, start - index);
        const right = Math.min(length, end - index);
        const nodeText = text.substring(left, right);
        const annotationLeft = Math.max(0, index - start);
        const annotationRight = Math.min(end - start, index + length - start);
        const annotationText =
            annotation.text.substring(annotationLeft, annotationRight);
        // Text has changed, forget the rest of this annotation.
        if (nodeText != annotationText) {
          log({
            reason: 'mismatch',
            nodeText: nodeText,
            annotationText: annotationText,
          });
          failures++;
          annotationIndex++;
          continue;
        }
        replacements.push(new Replacement(
            annotationIndex, left, right, nodeText, annotation.type,
            annotation.text, annotation.data));
        // If annotation is completed, move to next annotation and retry on
        // this node to fit more annotations if needed.
        if (end <= index + length) {
          annotationIndex++;
          continue;
        }
      }
      break;
    }

    // If the hit on a link, do not stylize. The check doesn't happen before
    // the annotation loop above, to keep the running cursor's (annotationIndex)
    // integrity.
    let currentParentNode: Node|null = node.parentNode;
    while (currentParentNode) {
      if (currentParentNode instanceof HTMLElement &&
          currentParentNode.tagName === 'A') {
        replacements = [];
        break;
      }
      currentParentNode = currentParentNode.parentNode;
    }

    replaceNode(node, replacements, text);

    return annotationIndex < annotations.length;
  });

  // Any remaining annotations left untouched are failures.
  failures += annotations.length - annotationIndex;

  sendWebKitMessage('annotations', {
    command: 'annotations.decoratingComplete',
    successes: annotations.length - failures,
    annotations: annotations.length
  });
}

/**
 * Remove current decorations.
 */
function removeDecorations(): void {
  for (let decoration of decorations) {
    const replacements = decoration.replacements;
    const parentNode = replacements[0]!.parentNode;
    if (!parentNode)
      return;
    parentNode.insertBefore(decoration.original, replacements[0]!);
    for (let replacement of replacements) {
      parentNode.removeChild(replacement);
    }
  }
  decorations = [];
}

/**
 * Removes any highlight on all annotations.
 */
 function removeHighlight(): void {
  for (let decoration of decorations) {
    for (let replacement of decoration.replacements) {
      if (!(replacement instanceof HTMLElement)) {
        continue;
      }
      replacement.style.color = "";
      replacement.style.background = "";
    }
  }
}

// Mark: Private helper functions

/**
 * Traverse the DOM tree starting at `root` and call process on each text
 * node.
 * @param root - root node where to start traversal.
 * @param process - callback for each text node.
 * @param includeShadowDOM - when true, shadow DOM is also traversed.
 */
function enumerateTextNodes(
    root: Node, process: EnumNodesFunction,
    includeShadowDOM: boolean = true): void {
  const nodes: Node[] = [root];
  let index = 0;

  while (nodes.length > 0) {
    let node = nodes.pop();
    if (!node) {
      break;
    }

    // Formatting and filtering.
    if (node.nodeType === Node.ELEMENT_NODE) {
      // Reject non-text nodes such as scripts.
      if (NON_TEXT_NODE_NAMES.has(node.nodeName)) {
        continue;
      }
      if (node.nodeName === 'BR') {
        if (!process(node, index, '\n'))
          break;
        index += 1;
        continue;
      }
      const style = window.getComputedStyle(node as Element);
      // Only proceed if the element is visible.
      if (style.display === 'none' || style.visibility === 'hidden') {
        continue;
      }
      // No need to add a line break before `body` as it is the first element.
      if (node.nodeName.toUpperCase() !== 'BODY' &&
          style.display !== 'inline') {
        if (!process(node, index, '\n'))
          break;
        index += 1;
      }

      if (includeShadowDOM) {
        const element = node as Element;
        if (element.shadowRoot && element.shadowRoot != node) {
          nodes.push(element.shadowRoot);
          continue;
        }
      }
    }

    if (node.hasChildNodes()) {
      for (let childIdx = node.childNodes.length - 1; childIdx >= 0;
           childIdx--) {
        nodes.push(node.childNodes[childIdx]!);
      }
    } else if (node.nodeType === Node.TEXT_NODE && node.textContent) {
      if (!process(node, index, node.textContent))
        break;
      index += node.textContent.length;
    }
  }
}

/**
 * Alternative to `enumerateTextNodes` using sections.
 */
function enumerateSectionsNodes(process: EnumNodesFunction): void {
  for (let section of sections) {
    const node: Node|undefined = WeakRef ?
        (section.node as WeakRef<Node>).deref() :
        section.node as Node;
    if (!node)
      continue;

    const text: string|null =
        node.nodeType === Node.ELEMENT_NODE ? '\n' : node.textContent;
    if (text && !process(node, section.index, text))
      break;
  }
}

/**
 * Returns first `maxChars` text characters from the page.
 * @param maxChars - maximum number of characters to parse out.
 */
function getPageText(maxChars: number): string {
  const parts: string[] = [];
  sections = [];
  enumerateTextNodes(document.body, function(node, index, text) {
    sections.push(new Section(WeakRef ? new WeakRef<Node>(node) : node, index));
    if (index + text.length > maxChars) {
      parts.push(text.substring(0, maxChars - index));
    } else {
      parts.push(text);
    }
    return index + text.length < maxChars;
  });
  return ''.concat(...parts);
}

let mutationDuringClickObserver: MutationsDuringClickTracker|null;

// Initiates a `mutationDuringClickObserver` that will be checked at document
// level tab handler (`handleTopTap`), where it will be decided if any action
// bubbling to objc is required (i.e. no DOM change occurs).
function handleTap(event: Event) {
  mutationDuringClickObserver = new MutationsDuringClickTracker(event);
}

// Monitors taps at the top, document level. This checks if it is tap
// triggered by an annotation and if no DOM mutation have happened while the
// event is bubbling up. If it's the case, the annotation callback is called.
function handleTopTap(event: Event) {
  // Nothing happened to the page between `handleTap` and `handleTopTap`.
  if (event.target instanceof HTMLElement &&
      event.target.tagName === 'CHROME_ANNOTATION' &&
      mutationDuringClickObserver &&
      !mutationDuringClickObserver.hasPreventativeActivity(event)) {
    const annotation = event.target;

    highlightAnnotation(annotation);

    sendWebKitMessage('annotations', {
      command: 'annotations.onClick',
      data: annotation.dataset['data'],
      rect: rectFromElement(annotation),
      text: annotation.dataset['annotation'],
    });
  }
  mutationDuringClickObserver?.stopObserving();
  mutationDuringClickObserver = null;
}

/**
 * Highlights all elements related to the annotation of which `annotation` is an
 * element of.
 */
function highlightAnnotation(annotation: HTMLElement) {
  // Using webkit edit selection kills a second tapping on the element and also
  // causes a merge with the edit menu in some circumstance.
  // Using custom highlight instead.
  for (let decoration of decorations) {
    for (let replacement of decoration.replacements) {
      if (!(replacement instanceof HTMLElement)) {
        continue;
      }
      if (replacement.tagName === 'CHROME_ANNOTATION' &&
          replacement.dataset['index'] === annotation.dataset['index']) {
        replacement.style.color = highlightTextColor;
        replacement.style.backgroundColor = highlightBackgroundColor;
      }
    }
  }
}

/**
 * Sorts and removes olverlappings annotations.
 * @param annotations - input annotations, cleaned in-place.
 */
function removeOverlappingAnnotations(annotations: Annotation[]): void {
  // Sort the annotations.
  annotations.sort((a, b) => {
    return a.start - b.start;
  });

  // Remove overlaps (lower indexed annotation has priority).
  let previous: Annotation|undefined = undefined;
  annotations.filter((annotation) => {
    if (previous && previous.start < annotation.end &&
        previous.end > annotation.start) {
      return false;
    }
    previous = annotation;
    return true;
  });
}

/**
 * Removes `node` from the DOM and replaces it with elements described by
 * `replacements` for the given `text`.
 */
function replaceNode(
    node: Node, replacements: Replacement[], text: string): void {
  const parentNode: Node|null = node.parentNode;

  if (replacements.length <= 0 || !parentNode) {
    return;
  }

  let textColor: string = decorationDefaultColor;
  if (parentNode instanceof Element) {
    textColor = window.getComputedStyle(parentNode).color || textColor;
  }

  let cursor = 0;
  const parts: Node[] = [];
  for (let replacement of replacements) {
    if (replacement.left > cursor) {
      parts.push(
          document.createTextNode(text.substring(cursor, replacement.left)));
    }
    const element = document.createElement('chrome_annotation');
    element.setAttribute('data-index', '' + replacement.index);
    element.setAttribute('data-data', replacement.data);
    element.setAttribute('data-annotation', replacement.annotationText);
    element.innerText = replacement.text;
    element.style.cssText = decorationStyles;
    element.style.borderBottomColor = textColor;
    element.addEventListener('click', handleTap.bind(element), true);
    parts.push(element);
    cursor = replacement.right;
  }
  if (cursor < text.length) {
    parts.push(document.createTextNode(text.substring(cursor, text.length)));
  }

  for (let part of parts) {
    parentNode.insertBefore(part, node);
  }
  parentNode.removeChild(node);

  // Keep track to be able to undo in `removeDecorations`.
  decorations.push(new Decoration(node, parts));
}

function rectFromElement(element: Element) {
  const domRect = element.getClientRects()[0];
  if (!domRect) {
    return {};
  }
  return {
    x: domRect.x,
    y: domRect.y,
    width: domRect.width,
    height: domRect.height
  };
}

gCrWeb.annotations = {
  extractText,
  decorateAnnotations,
  removeDecorations,
  removeHighlight,
};
