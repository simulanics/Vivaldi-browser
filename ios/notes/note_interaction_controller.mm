// Copyright (c) 2022 Vivaldi Technologies AS. All rights reserved

#import "ios/notes/note_interaction_controller.h"
#include <stdint.h>

#import <MaterialComponents/MaterialSnackbar.h>

#include "base/check_op.h"
#include "base/mac/foundation_util.h"
#include "base/notreached.h"
#include "base/strings/utf_string_conversions.h"
#include "base/time/time.h"
#include "ios/notes/notes_factory.h"
#include "ios/chrome/browser/browser_state/chrome_browser_state.h"
#import "ios/chrome/browser/main/browser.h"
#import "ios/chrome/browser/metrics/new_tab_page_uma.h"
#import "ios/notes/note_edit_view_controller.h"
#import "ios/notes/note_folder_editor_view_controller.h"
#import "ios/notes/note_folder_view_controller.h"
#import "ios/notes/note_home_view_controller.h"
#import "ios/notes/note_interaction_controller_delegate.h"
#import "ios/notes/note_mediator.h"
#import "ios/notes/note_navigation_controller.h"
#import "ios/notes/note_navigation_controller_delegate.h"
#import "ios/notes/note_path_cache.h"
#import "ios/notes/note_transitioning_delegate.h"
#import "ios/notes/note_utils_ios.h"
#include "notes/note_node.h"
#include "notes/notes_model.h"
#import "ios/chrome/browser/ui/commands/application_commands.h"
#import "ios/chrome/browser/ui/commands/browser_commands.h"
#import "ios/chrome/browser/ui/commands/open_new_tab_command.h"
#import "ios/chrome/browser/ui/default_promo/default_browser_utils.h"
#import "ios/chrome/browser/ui/table_view/table_view_navigation_controller.h"
#import "ios/chrome/browser/ui/table_view/table_view_presentation_controller.h"
#import "ios/chrome/browser/ui/table_view/table_view_presentation_controller_delegate.h"
#include "ios/chrome/browser/ui/util/uikit_ui_util.h"
#import "ios/chrome/browser/ui/util/url_with_title.h"
#import "ios/chrome/browser/url_loading/url_loading_browser_agent.h"
#import "ios/chrome/browser/url_loading/url_loading_params.h"
#import "ios/chrome/browser/url_loading/url_loading_util.h"
#import "ios/chrome/browser/web_state_list/web_state_list.h"
#include "ios/chrome/grit/ios_strings.h"
#import "ios/web/public/navigation/navigation_manager.h"
#include "ios/web/public/navigation/referrer.h"
#import "ios/web/public/web_state.h"

#import "ios/notes/note_folder_view_controller.h"

#include "app/vivaldi_apptools.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

using vivaldi::NotesModel;
using vivaldi::NoteNode;

namespace {

// Tracks the type of UI that is currently being presented.
enum class PresentedState {
  NONE,
  NOTE_BROWSER,
  NOTE_EDITOR,
  NOTE_FOLDER_EDITOR,
  NOTE_FOLDER_SELECTION,
};

}  // namespace

@interface NoteInteractionController () <
    NoteEditViewControllerDelegate,
    NoteFolderEditorViewControllerDelegate,
    NoteFolderViewControllerDelegate,
    NoteHomeViewControllerDelegate,
    TableViewPresentationControllerDelegate> {
  // The browser notes are presented in.
  Browser* _browser;  // weak

  // The browser state of the current user.
  ChromeBrowserState* _currentBrowserState;  // weak

  // The browser state to use, might be different from _currentBrowserState if
  // it is incognito.
  ChromeBrowserState* _browserState;  // weak

  // The parent controller on top of which the UI needs to be presented.
  __weak UIViewController* _parentController;

  // The web state list currently in use.
  WebStateList* _webStateList;
}

// The type of view controller that is being presented.
@property(nonatomic, assign) PresentedState currentPresentedState;

// The delegate provided to |self.noteNavigationController|.
@property(nonatomic, strong)
    NoteNavigationControllerDelegate* noteNavigationControllerDelegate;

// The note model in use.
@property(nonatomic, assign) NotesModel* noteModel;

// A reference to the potentially presented note browser. This will be
// non-nil when |currentPresentedState| is NOTE_BROWSER.
@property(nonatomic, strong) NoteHomeViewController* noteBrowser;

// A reference to the potentially presented single note editor. This will be
// non-nil when |currentPresentedState| is NOTE_EDITOR.
@property(nonatomic, strong) NoteEditViewController* noteEditor;

// A reference to the potentially presented folder editor. This will be non-nil
// when |currentPresentedState| is FOLDER_EDITOR.
@property(nonatomic, strong) NoteFolderEditorViewController* folderEditor;

// A reference to the potentially presented folder selector. This will be
// non-nil when |currentPresentedState| is FOLDER_SELECTION.
@property(nonatomic, strong) NoteFolderViewController* folderSelector;

@property(nonatomic, copy) void (^folderSelectionCompletionBlock)
    (const vivaldi::NoteNode*);

@property(nonatomic, strong) NoteMediator* mediator;

@property(nonatomic, readonly, weak) id<ApplicationCommands, BrowserCommands>
    handler;

// The transitioning delegate that is used when presenting
// |self.noteBrowser|.
@property(nonatomic, strong)
    NoteTransitioningDelegate* noteTransitioningDelegate;

// Dismisses the note browser.  If |urlsToOpen| is not empty, then the user
// has selected to navigate to those URLs with specified tab mode.
- (void)dismissNoteBrowserAnimated:(BOOL)animated
                            urlsToOpen:(const std::vector<GURL>&)urlsToOpen
                           inIncognito:(BOOL)inIncognito
                                newTab:(BOOL)newTab;

// Dismisses the note editor.
- (void)dismissNoteEditorAnimated:(BOOL)animated;

// Dismisses the folder editor.
- (void)dismissFolderEditorAnimated:(BOOL)animated;

@end

@implementation NoteInteractionController
@synthesize noteBrowser = _noteBrowser;
@synthesize noteEditor = _noteEditor;
@synthesize noteModel = _noteModel;
@synthesize noteNavigationController = _noteNavigationController;
@synthesize noteNavigationControllerDelegate =
    _noteNavigationControllerDelegate;
@synthesize noteTransitioningDelegate = _noteTransitioningDelegate;
@synthesize currentPresentedState = _currentPresentedState;
@synthesize delegate = _delegate;
@synthesize handler = _handler;
@synthesize folderEditor = _folderEditor;
@synthesize mediator = _mediator;

- (instancetype)initWithBrowser:(Browser*)browser
               parentController:(UIViewController*)parentController {
  self = [super init];
  if (self) {
    _browser = browser;
    // notes are always opened with the main browser state, even in
    // incognito mode.
    _currentBrowserState = browser->GetBrowserState();
    _browserState = _currentBrowserState->GetOriginalChromeBrowserState();
    _parentController = parentController;
    // TODO(crbug.com/1045047): Use HandlerForProtocol after commands protocol
    // clean up.
    _handler = static_cast<id<ApplicationCommands, BrowserCommands>>(
        browser->GetCommandDispatcher());
    _webStateList = browser->GetWebStateList();
    _noteModel =
        vivaldi::NotesModelFactory::GetForBrowserState(_browserState);
    _mediator = [[NoteMediator alloc] initWithBrowserState:_browserState];
    _currentPresentedState = PresentedState::NONE;
    DCHECK(_noteModel);
    DCHECK(_parentController);
  }
  return self;
}

- (void)dealloc {
  [self shutdown];
}

- (void)shutdown {
  [self noteBrowserDismissed];

  _noteBrowser.homeDelegate = nil;
  [_noteBrowser shutdown];
  _noteBrowser = nil;

  _noteEditor.delegate = nil;
  [_noteEditor shutdown];
  _noteEditor = nil;

  _panelDelegate = nil;
  _noteNavigationController = nil;
}

- (void)showNotes {
  DCHECK_EQ(PresentedState::NONE, self.currentPresentedState);
  DCHECK(!self.noteNavigationController);
  self.noteBrowser =
      [[NoteHomeViewController alloc] initWithBrowser:_browser];
  self.noteBrowser.homeDelegate = self;
  NSArray<NoteHomeViewController*>* replacementViewControllers = nil;
  if (self.noteModel->loaded()) {
    // Set the root node if the model has been loaded. If the model has not been
    // loaded yet, the root node will be set in NoteHomeViewController after
    // the model is finished loading.
    [self.noteBrowser setRootNode:self.noteModel->root_node()];
    replacementViewControllers =
       [self.noteBrowser cachedViewControllerStack];
  }

  self.currentPresentedState = PresentedState::NOTE_BROWSER;
  [self showHomeViewController:self.noteBrowser
      withReplacementViewControllers:replacementViewControllers];
}

- (void)presentFolderPickerWithCompletion:
    (void (^)(const vivaldi::NoteNode*))block {
  DCHECK_EQ(PresentedState::NONE, self.currentPresentedState);
  DCHECK(block);

  [self dismissSnackbar];

  self.currentPresentedState = PresentedState::NOTE_FOLDER_SELECTION;
  self.folderSelectionCompletionBlock = [block copy];

  std::set<const NoteNode*> editedNodes;
  self.folderSelector = [[NoteFolderViewController alloc]
      initWithNotesModel:self.noteModel
           allowsNewFolders:YES
                editedNodes:editedNodes
               allowsCancel:YES
             selectedFolder:nil
                    browser:_browser];
  self.folderSelector.delegate = self;

  [self presentTableViewController:self.folderSelector
      withReplacementViewControllers:nil];
}

- (void)presentEditorForNode:(const vivaldi::NoteNode*)node {
  DCHECK_EQ(PresentedState::NONE, self.currentPresentedState);
  [self dismissSnackbar];

  if (!node) {
    return;
  }

  if (!(node->type() == NoteNode::NOTE ||
        node->type() == NoteNode::FOLDER)) {
    return;
  }

  ChromeTableViewController<UIAdaptivePresentationControllerDelegate>*
      editorController = nil;
  if (node->type() == vivaldi::NoteNode::NOTE) {
    self.currentPresentedState = PresentedState::NOTE_EDITOR;
    NoteEditViewController* noteEditor =
        [[NoteEditViewController alloc] initWithNote:node
                                                     browser:_browser];
    self.noteEditor = noteEditor;
    self.noteEditor.delegate = self;
    editorController = noteEditor;
  } else if (node->type() == NoteNode::FOLDER) {
    self.currentPresentedState = PresentedState::NOTE_FOLDER_EDITOR;
    NoteFolderEditorViewController* folderEditor =
        [NoteFolderEditorViewController
            folderEditorWithNotesModel:self.noteModel
                                   folder:node
                                  browser:_browser];
    folderEditor.delegate = self;
    self.folderEditor = folderEditor;
    editorController = folderEditor;
  } else {
    NOTREACHED();
  }

  [self presentTableViewController:editorController
      withReplacementViewControllers:nil];
}

- (void)dismissNoteBrowserAnimated:(BOOL)animated
                            urlsToOpen:(const std::vector<GURL>&)urlsToOpen
                           inIncognito:(BOOL)inIncognito
                                newTab:(BOOL)newTab {
  if (self.currentPresentedState != PresentedState::NOTE_BROWSER) {
    return;
  }
  ProceduralBlock completion = ^{
    [self noteBrowserDismissed];
    [self.panelDelegate panelDismissed];
  };
  [self.noteBrowser dismissViewControllerAnimated:animated completion:completion];

  DCHECK(self.noteNavigationController);
  if (_parentController) {
    [_parentController dismissViewControllerAnimated:animated
                                          completion:nil];
  }
  self.currentPresentedState = PresentedState::NONE;
}

- (void)noteBrowserDismissed {
  // TODO(crbug.com/940856): Make sure navigaton
  // controller doesn't keep any controllers. Without
  // this there's a memory leak of (almost) every BHVC
  // the user visits.
  [self.noteNavigationController setViewControllers:@[] animated:NO];

  self.noteBrowser.homeDelegate = nil;
  self.noteBrowser = nil;
  self.noteTransitioningDelegate = nil;
  self.noteNavigationController = nil;
  self.noteNavigationControllerDelegate = nil;
}

- (void)dismissNoteEditorAnimated:(BOOL)animated {
  if (self.currentPresentedState != PresentedState::NOTE_EDITOR)
    return;
  DCHECK(self.noteNavigationController);

  self.noteEditor.delegate = nil;
  self.noteEditor = nil;
  [self.noteNavigationController
      dismissViewControllerAnimated:animated
                         completion:^{
                           self.noteNavigationController = nil;
                           self.noteTransitioningDelegate = nil;
                         }];
  self.currentPresentedState = PresentedState::NONE;
}

- (void)dismissFolderEditorAnimated:(BOOL)animated {
  if (self.currentPresentedState != PresentedState::NOTE_FOLDER_EDITOR)
    return;
  DCHECK(self.noteNavigationController);

  [self.noteNavigationController
      dismissViewControllerAnimated:animated
                         completion:^{
                           self.folderEditor.delegate = nil;
                           self.folderEditor = nil;
                           self.noteNavigationController = nil;
                           self.noteTransitioningDelegate = nil;
                         }];
  self.currentPresentedState = PresentedState::NONE;
}

- (void)dismissFolderSelectionAnimated:(BOOL)animated {
  if (self.currentPresentedState != PresentedState::NOTE_FOLDER_SELECTION)
    return;
  DCHECK(self.noteNavigationController);

  [self.noteNavigationController
      dismissViewControllerAnimated:animated
                         completion:^{
                           self.folderSelector.delegate = nil;
                           self.folderSelector = nil;
                           self.noteNavigationController = nil;
                           self.noteTransitioningDelegate = nil;
                         }];
  self.currentPresentedState = PresentedState::NONE;
}

- (void)dismissNoteModalControllerAnimated:(BOOL)animated {
  // No urls to open.  So it does not care about inIncognito and newTab.
  [self dismissNoteBrowserAnimated:animated
                            urlsToOpen:std::vector<GURL>()
                           inIncognito:NO
                                newTab:NO];
  [self dismissNoteEditorAnimated:animated];
}

- (void)dismissSnackbar {
   //Dismiss any note related snackbar this controller could have presented.
  [MDCSnackbarManager.defaultManager
      dismissAndCallCompletionBlocksWithCategory:
          note_utils_ios::kNotesSnackbarCategory];
}

#pragma mark - NoteEditViewControllerDelegate

- (BOOL)noteEditor:(NoteEditViewController*)controller
    shoudDeleteAllOccurencesOfNote:(const NoteNode*)note {
  return YES;
}

- (void)noteEditorWantsDismissal:(NoteEditViewController*)controller {
  [self dismissNoteEditorAnimated:YES];
}

- (void)noteEditorWillCommitContentChange:
    (NoteEditViewController*)controller {
  [self.delegate noteInteractionControllerWillCommitContentChange:self];
}

#pragma mark - NoteFolderEditorViewControllerDelegate

- (void)noteFolderEditor:(NoteFolderEditorViewController*)folderEditor
      didFinishEditingFolder:(const NoteNode*)folder {
  DCHECK(folder);
  [self dismissFolderEditorAnimated:YES];
}

- (void)noteFolderEditorDidDeleteEditedFolder:
    (NoteFolderEditorViewController*)folderEditor {
  [self dismissFolderEditorAnimated:YES];
}

- (void)noteFolderEditorDidCancel:
    (NoteFolderEditorViewController*)folderEditor {
  [self dismissFolderEditorAnimated:YES];
}

- (void)noteFolderEditorWillCommitTitleChange:
    (NoteFolderEditorViewController*)controller {
  [self.delegate noteInteractionControllerWillCommitContentChange:self];
}

#pragma mark - NoteFolderViewControllerDelegate

- (void)folderPicker:(NoteFolderViewController*)folderPicker
    didFinishWithFolder:(const  vivaldi::NoteNode*)folder {
  [self dismissFolderSelectionAnimated:YES];

  if (self.folderSelectionCompletionBlock) {
    self.folderSelectionCompletionBlock(folder);
  }
}

- (void)folderPickerDidCancel:(NoteFolderViewController*)folderPicker {
  [self dismissFolderSelectionAnimated:YES];
}

- (void)folderPickerDidDismiss:(NoteFolderViewController*)folderPicker {
  [self dismissFolderSelectionAnimated:YES];
}

#pragma mark - NoteHomeViewControllerDelegate

- (void)
noteHomeViewControllerWantsDismissal:(NoteHomeViewController*)controller
                        navigationToUrls:(const std::vector<GURL>&)urls {
  [self noteHomeViewControllerWantsDismissal:controller
                                navigationToUrls:urls
                                     inIncognito:_currentBrowserState
                                                     ->IsOffTheRecord()
                                          newTab:NO];
}

- (void)noteHomeViewControllerWantsDismissal:
            (NoteHomeViewController*)controller
                                navigationToUrls:(const std::vector<GURL>&)urls
                                     inIncognito:(BOOL)inIncognito
                                          newTab:(BOOL)newTab {
  [self dismissNoteBrowserAnimated:YES
                            urlsToOpen:urls
                           inIncognito:inIncognito
                                newTab:newTab];
}

#pragma mark - TableViewPresentationControllerDelegate

- (BOOL)presentationControllerShouldDismissOnTouchOutside:
    (TableViewPresentationController*)controller {
  return NO;
}

- (void)presentationControllerWillDismiss:
    (TableViewPresentationController*)controller {
  [self dismissNoteModalControllerAnimated:YES];
}

#pragma mark - Private

// Presents |viewController| using the appropriate presentation and styling,
// depending on whether the UIRefresh experiment is enabled or disabled. Sets
// |self.noteNavigationController| to the UINavigationController subclass
// used, and may set |self.noteNavigationControllerDelegate| or
// |self.noteTransitioningDelegate| depending on whether or not the desired
// transition requires those objects.  If |replacementViewControllers| is not
// nil, those controllers are swapped in to the UINavigationController instead
// of |viewController|.
// Presents the diff controllers from note browser.
// Note browser is not presented but shown in page view controller
- (void)presentTableViewController:
            (ChromeTableViewController<
                UIAdaptivePresentationControllerDelegate>*)viewController
    withReplacementViewControllers:
        (NSArray<ChromeTableViewController*>*)replacementViewControllers {
  TableViewNavigationController* navController =
      [[TableViewNavigationController alloc] initWithTable:viewController];
  self.noteNavigationController = navController;
  if (replacementViewControllers) {
    [navController setViewControllers:replacementViewControllers];
  }

  navController.toolbarHidden = YES;
  self.noteNavigationControllerDelegate =
      [[NoteNavigationControllerDelegate alloc] init];
  navController.delegate = self.noteNavigationControllerDelegate;
  if (self.currentPresentedState != PresentedState::NOTE_BROWSER) {
    [_parentController presentViewController:navController
                                  animated:YES
                                completion:nil];
  }
}

- (void)showHomeViewController:
            (ChromeTableViewController<
                UIAdaptivePresentationControllerDelegate>*)viewController
    withReplacementViewControllers:
        (NSArray<ChromeTableViewController*>*)replacementViewControllers {
  TableViewNavigationController* navController =
      [[TableViewNavigationController alloc] initWithTable:viewController];
  self.noteNavigationController = navController;
  if (replacementViewControllers) {
    [navController setViewControllers:replacementViewControllers];
  }

  navController.toolbarHidden = YES;
  self.noteNavigationControllerDelegate =
      [[NoteNavigationControllerDelegate alloc] init];
  navController.delegate = self.noteNavigationControllerDelegate;
}

@end
