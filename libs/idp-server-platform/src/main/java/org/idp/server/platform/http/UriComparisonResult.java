/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.http;

/**
 * Result of URI comparison for redirect_uri validation.
 *
 * <p>RFC 6749 Section 3.1.2.3 requires simple string comparison, but RFC 3986 Section 6.2 defines
 * normalization rules that can be applied for URI comparison.
 */
public enum UriComparisonResult {
  /** URIs match exactly (simple string comparison per RFC 6749 Section 3.1.2.3) */
  SIMPLE_MATCH,

  /** URIs match after syntax-based normalization (RFC 3986 Section 6.2.2) */
  NORMALIZED_MATCH,

  /** URIs do not match even after normalization */
  NO_MATCH;

  public boolean isMatch() {
    return this == SIMPLE_MATCH || this == NORMALIZED_MATCH;
  }
}
