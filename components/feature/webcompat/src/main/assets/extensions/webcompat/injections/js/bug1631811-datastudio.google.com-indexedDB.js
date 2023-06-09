/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

"use strict";

/**
 * Bug 1631811 - disable indexedDB for datastudio.google.com iframes
 *
 * Indexed DB is disabled already for these iframes due to cookie blocking.
 * This intervention changes the functionality from throwing a SecurityError
 * when indexedDB is accessed to removing it from the window object
 */

console.info(
  "window.indexedDB has been overwritten for compatibility reasons. See https://bugzilla.mozilla.org/show_bug.cgi?id=1631811 for details."
);

Object.defineProperty(window.wrappedJSObject, "indexedDB", {
  get: undefined,
  set: undefined,
});
