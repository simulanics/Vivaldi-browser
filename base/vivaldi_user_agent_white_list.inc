// Copyright (c) 2019 Vivaldi Technologies AS. All rights reserved

// List of domains that receive the user agen header with the Vivaldi/version
// string.
//
// All strings must be in the low-case and all lines must end with a comma.
// Empty lines and C++ comments can be used freely here. The order is not
// important, but keep the list ordered alphabetically within a block.

// Vivaldi hosts
"vivaldi.com",
"vivaldi.net",

// This is so various test scripts will see Vivaldi in the user agent
"tests.viv.int",

// Partners with branding for Vivaldi
"duckduckgo.com",
"ecosia.com",
"ecosia.org",
"neeva.com",
"qwant.com",
"startpage.com",

// Sites that fails or use messages like Add to Chrome

// VB-65894
"mojeek.com",
"kagi.com",

// VB-64209: Videos on ted.com fail if we use the chrome user agent.
"ted.com",

// VB-81592: Used in redirects, e.g to Chrome Web Store
"vvld.in",
