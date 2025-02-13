// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/public/provider/chrome/browser/signin/fake_chrome_identity.h"

#import "base/mac/foundation_util.h"
#import "base/strings/sys_string_conversions.h"

#if !defined(__has_feature) || !__has_feature(objc_arc)
#error "This file requires ARC support."
#endif

namespace {
NSString* const kCoderUserEmailKey = @"UserEmail";
NSString* const kCoderGaiaIDKey = @"GaiaID";
NSString* const kCoderUserFullNameKey = @"UserFullName";
NSString* const kCoderUserGivenNameKey = @"UserGivenName";
NSString* const kCoderHashedGaiaIDKey = @"HashedGaiaID";
}  // namespace

@implementation FakeChromeIdentity

@synthesize userEmail = _userEmail;
@synthesize gaiaID = _gaiaID;
@synthesize userFullName = _userFullName;
@synthesize userGivenName = _userGivenName;
@synthesize hashedGaiaID = _hashedGaiaID;

+ (std::string)encodeIdentitiesToBase64:
    (NSArray<FakeChromeIdentity*>*)identities {
  NSError* error = nil;
  NSData* data = [NSKeyedArchiver archivedDataWithRootObject:identities
                                       requiringSecureCoding:NO
                                                       error:&error];
  DCHECK(!error);
  NSString* string = [data base64EncodedStringWithOptions:
                               NSDataBase64EncodingEndLineWithCarriageReturn];
  return base::SysNSStringToUTF8(string);
}

+ (NSArray<FakeChromeIdentity*>*)identitiesFromBase64String:
    (const std::string&)string {
  NSData* data = [[NSData alloc]
      initWithBase64EncodedString:base::SysUTF8ToNSString(string)
                          options:NSDataBase64DecodingIgnoreUnknownCharacters];
  NSSet* classes =
      [NSSet setWithArray:@[ [NSArray class], [FakeChromeIdentity class] ]];
  NSError* error = nil;
  NSArray* identities = [NSKeyedUnarchiver unarchivedObjectOfClasses:classes
                                                            fromData:data
                                                               error:&error];
  return identities;
}

+ (FakeChromeIdentity*)fakeIdentity1 {
  return [FakeChromeIdentity identityWithEmail:@"foo1@gmail.com"
                                        gaiaID:@"foo1ID"
                                          name:@"Fake Foo 1"];
}

+ (FakeChromeIdentity*)fakeIdentity2 {
  return [FakeChromeIdentity identityWithEmail:@"foo2@gmail.com"
                                        gaiaID:@"foo2ID"
                                          name:@"Fake Foo 2"];
}

+ (FakeChromeIdentity*)fakeManagedIdentity {
  return [FakeChromeIdentity identityWithEmail:@"foo@google.com"
                                        gaiaID:@"fooManagedID"
                                          name:@"Fake Managed"];
}

+ (FakeChromeIdentity*)identityWithEmail:(NSString*)email
                                  gaiaID:(NSString*)gaiaID
                                    name:(NSString*)name {
  return [[FakeChromeIdentity alloc] initWithEmail:email
                                            gaiaID:gaiaID
                                              name:name];
}

- (instancetype)initWithEmail:(NSString*)email
                       gaiaID:(NSString*)gaiaID
                         name:(NSString*)name {
  self = [super init];
  if (self) {
    _userEmail = [email copy];
    _gaiaID = [gaiaID copy];
    _userFullName = [name copy];
    _userGivenName = [name copy];
    _hashedGaiaID = [NSString stringWithFormat:@"%@_hashID", name];
  }
  return self;
}

- (NSString*)userEmail {
  return _userEmail;
}

- (NSString*)gaiaID {
  return _gaiaID;
}

- (NSString*)userFullName {
  return _userFullName;
}

- (NSString*)userGivenName {
  return _userGivenName;
}

- (NSString*)hashedGaiaID {
  return _hashedGaiaID;
}

// Overrides the method to return YES so that the object will be passed by value
// in EDO. This requires the object confirm to NSSecureCoding protocol.
- (BOOL)edo_isEDOValueType {
  return YES;
}

// Overrides `isEqual` and `hash` methods to compare objects by values. This is
// useful when the object is passed by value between processes in EG2.
- (BOOL)isEqual:(id)object {
  if (self == object) {
    return YES;
  }
  if ([object isKindOfClass:self.class]) {
    FakeChromeIdentity* other =
        base::mac::ObjCCastStrict<FakeChromeIdentity>(object);
    return [_userEmail isEqualToString:other.userEmail] &&
           [_gaiaID isEqualToString:other.gaiaID] &&
           [_userFullName isEqualToString:other.userFullName] &&
           [_userGivenName isEqualToString:other.userGivenName] &&
           [_hashedGaiaID isEqualToString:other.hashedGaiaID];
  }
  return NO;
}

- (NSUInteger)hash {
  return _gaiaID.hash;
}

#pragma mark - NSSecureCoding

- (void)encodeWithCoder:(NSCoder*)coder {
  [coder encodeObject:_userEmail forKey:kCoderUserEmailKey];
  [coder encodeObject:_gaiaID forKey:kCoderGaiaIDKey];
  [coder encodeObject:_userFullName forKey:kCoderUserFullNameKey];
  [coder encodeObject:_userGivenName forKey:kCoderUserGivenNameKey];
  [coder encodeObject:_hashedGaiaID forKey:kCoderHashedGaiaIDKey];
}

- (id)initWithCoder:(NSCoder*)coder {
  if ((self = [super init])) {
    _userEmail = [coder decodeObjectOfClass:[NSString class]
                                     forKey:kCoderUserEmailKey];
    _gaiaID = [coder decodeObjectOfClass:[NSString class]
                                  forKey:kCoderGaiaIDKey];
    _userFullName = [coder decodeObjectOfClass:[NSString class]
                                        forKey:kCoderUserFullNameKey];
    _userGivenName = [coder decodeObjectOfClass:[NSString class]
                                         forKey:kCoderUserGivenNameKey];
    _hashedGaiaID = [coder decodeObjectOfClass:[NSString class]
                                        forKey:kCoderHashedGaiaIDKey];
  }
  return self;
}

+ (BOOL)supportsSecureCoding {
  return YES;
}

@end
