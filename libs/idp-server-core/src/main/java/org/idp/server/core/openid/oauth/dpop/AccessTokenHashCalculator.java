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

package org.idp.server.core.openid.oauth.dpop;

import org.idp.server.platform.base64.Base64Codeable;
import org.idp.server.platform.hash.MessageDigestable;

/**
 * Access Token Hash Calculator for DPoP ath claim.
 *
 * <p>Computes the base64url-encoded SHA-256 hash of an ASCII access token value, as required by the
 * ath (access token hash) claim in DPoP proofs per RFC 9449 Section 4.2.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.2">RFC 9449 Section 4.2</a>
 */
public class AccessTokenHashCalculator implements Base64Codeable, MessageDigestable {

  String accessTokenValue;

  public AccessTokenHashCalculator(String accessTokenValue) {
    this.accessTokenValue = accessTokenValue;
  }

  public String calculate() {
    byte[] hash = digestWithSha256(accessTokenValue);
    return encodeWithUrlSafe(hash);
  }
}
