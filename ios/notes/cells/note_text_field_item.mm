// Copyright 2022 Vivaldi Technologies AS. All rights reserved.

#import "ios/notes/cells/note_text_field_item.h"

#include "base/check_op.h"
#include "base/mac/foundation_util.h"
#import "ios/notes/note_ui_constants.h"
#import "ios/notes/note_utils_ios.h"
#import "ios/chrome/browser/ui/util/uikit_ui_util.h"
#import "ios/chrome/common/ui/colors/semantic_color_names.h"
#import "ios/chrome/common/ui/util/constraints_ui_util.h"
#include "ios/chrome/grit/ios_strings.h"
#include "ui/base/l10n/l10n_util_mac.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

#pragma mark - NoteTextFieldItem

@implementation NoteTextFieldItem

@synthesize text = _text;
@synthesize placeholder = _placeholder;
@synthesize delegate = _delegate;

- (instancetype)initWithType:(NSInteger)type {
  self = [super initWithType:type];
  self.cellClass = [NoteTextFieldCell class];
  return self;
}

#pragma mark TableViewItem

- (void)configureCell:(TableViewCell*)tableCell
           withStyler:(ChromeTableViewStyler*)styler {
  [super configureCell:tableCell withStyler:styler];

  NoteTextFieldCell* cell =
      base::mac::ObjCCastStrict<NoteTextFieldCell>(tableCell);
  cell.textField.text = self.text;
  cell.titleLabel.text = self.placeholder;
  cell.textField.placeholder = self.placeholder;
  cell.textField.tag = self.type;
  [cell.textField addTarget:self
                     action:@selector(textFieldDidChange:)
           forControlEvents:UIControlEventEditingChanged];
  cell.textField.delegate = self.delegate;
  cell.textField.accessibilityLabel = self.text;
  cell.textField.accessibilityIdentifier =
      [NSString stringWithFormat:@"%@_textField", self.accessibilityIdentifier];
  cell.selectionStyle = UITableViewCellSelectionStyleNone;
}

#pragma mark UIControlEventEditingChanged

- (void)textFieldDidChange:(UITextField*)textField {
  DCHECK_EQ(textField.tag, self.type);
  self.text = textField.text;
  [self.delegate textDidChangeForItem:self];
}

@end

#pragma mark - NoteTextFieldCell

@interface NoteTextFieldCell ()
// Stack view to display label / value which we'll switch from horizontal to
// vertical based on preferredContentSizeCategory.
@property(nonatomic, strong) UIStackView* stackView;
@end

@implementation NoteTextFieldCell
@synthesize textField = _textField;
@synthesize titleLabel = _titleLabel;
@synthesize stackView = _stackView;

- (instancetype)initWithStyle:(UITableViewCellStyle)style
              reuseIdentifier:(NSString*)reuseIdentifier {
  self = [super initWithStyle:style reuseIdentifier:reuseIdentifier];
  if (!self)
    return nil;

  // Label.
  self.titleLabel = [[UILabel alloc] init];
  self.titleLabel.font = [UIFont preferredFontForTextStyle:UIFontTextStyleBody];
  self.titleLabel.adjustsFontForContentSizeCategory = YES;
  [self.titleLabel setContentHuggingPriority:UILayoutPriorityRequired
                                     forAxis:UILayoutConstraintAxisHorizontal];
  [self.titleLabel
      setContentCompressionResistancePriority:UILayoutPriorityRequired
                                      forAxis:UILayoutConstraintAxisHorizontal];
  [self.titleLabel
      setContentCompressionResistancePriority:UILayoutPriorityRequired
                                      forAxis:UILayoutConstraintAxisVertical];

  // Textfield.
  self.textField = [[UITextField alloc] init];
  self.textField.font = [UIFont preferredFontForTextStyle:UIFontTextStyleBody];
  self.textField.adjustsFontForContentSizeCategory = YES;
  self.textField.textColor = [NoteTextFieldCell textColorForEditing:NO];
  self.textField.clearButtonMode = UITextFieldViewModeWhileEditing;
  self.textField.textAlignment = NSTextAlignmentRight;
  [self.textField setContentHuggingPriority:UILayoutPriorityDefaultLow
                                    forAxis:UILayoutConstraintAxisHorizontal];
  [self.textField
      setContentCompressionResistancePriority:UILayoutPriorityRequired
                                      forAxis:UILayoutConstraintAxisVertical];

  // StackView.
  self.stackView = [[UIStackView alloc]
      initWithArrangedSubviews:@[ self.titleLabel, self.textField ]];
  self.stackView.axis = UILayoutConstraintAxisHorizontal;
  self.stackView.spacing = kNoteCellViewSpacing;
  self.stackView.distribution = UIStackViewDistributionFill;
  self.stackView.alignment = UIStackViewAlignmentCenter;
  [self.stackView
      setContentCompressionResistancePriority:UILayoutPriorityRequired
                                      forAxis:UILayoutConstraintAxisVertical];
  self.stackView.translatesAutoresizingMaskIntoConstraints = NO;
  [self.contentView addSubview:self.stackView];

  // Set up constraints.
  AddSameConstraintsToSidesWithInsets(
      self.stackView, self.contentView,
      LayoutSides::kLeading | LayoutSides::kTrailing | LayoutSides::kBottom |
          LayoutSides::kTop,
      ChromeDirectionalEdgeInsetsMake(
          kNoteCellVerticalInset, kNoteCellHorizontalLeadingInset,
          kNoteCellVerticalInset, kNoteCellHorizontalTrailingInset));

  [self applyContentSizeCategoryStyles];

  return self;
}

- (void)traitCollectionDidChange:(UITraitCollection*)previousTraitCollection {
  [super traitCollectionDidChange:previousTraitCollection];
  if (self.traitCollection.preferredContentSizeCategory !=
      previousTraitCollection.preferredContentSizeCategory) {
    [self applyContentSizeCategoryStyles];
  }
}

- (void)applyContentSizeCategoryStyles {
  if (UIContentSizeCategoryIsAccessibilityCategory(
          UIScreen.mainScreen.traitCollection.preferredContentSizeCategory)) {
    self.stackView.axis = UILayoutConstraintAxisVertical;
    self.stackView.alignment = UIStackViewAlignmentLeading;
    self.textField.textAlignment = NSTextAlignmentLeft;
  } else {
    self.stackView.axis = UILayoutConstraintAxisHorizontal;
    self.stackView.alignment = UIStackViewAlignmentCenter;
    self.textField.textAlignment = NSTextAlignmentRight;
  }
}

+ (UIColor*)textColorForEditing:(BOOL)editing {
  return editing ? [UIColor colorNamed:kTextPrimaryColor]
                 : [UIColor colorNamed:kTextSecondaryColor];
}

- (void)prepareForReuse {
  [super prepareForReuse];
  [self.textField resignFirstResponder];
  [self.textField removeTarget:nil
                        action:NULL
              forControlEvents:UIControlEventAllEvents];
  self.textField.delegate = nil;
  self.textField.text = nil;
}

@end
