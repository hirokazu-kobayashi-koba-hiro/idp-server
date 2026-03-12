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

package org.idp.server.core.openid.token.verifier;

import org.idp.server.core.openid.oauth.dpop.DPoPProofInvalidException;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifiedResult;
import org.idp.server.core.openid.token.AccessToken;

/**
 * Verifies DPoP key continuity for refresh token grants per RFC 9449 Section 10.
 *
 * <p>When an access token was originally issued with DPoP binding (cnf.jkt), the refresh token
 * request MUST include a DPoP proof signed with the same key. This prevents a downgrade attack
 * where a stolen refresh token could be used to obtain a Bearer token without sender constraint.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-10">RFC 9449 Section 10</a>
 */
public class RefreshTokenDPoPBindingVerifier {

  AccessToken originalAccessToken;
  DPoPProofVerifiedResult dpopResult;

  public RefreshTokenDPoPBindingVerifier(
      AccessToken originalAccessToken, DPoPProofVerifiedResult dpopResult) {
    this.originalAccessToken = originalAccessToken;
    this.dpopResult = dpopResult;
  }

  public void verify() {
    if (!originalAccessToken.hasDPoPBinding()) {
      return;
    }

    throwExceptionIfDPoPProofMissing();
    throwExceptionIfDPoPKeyMismatch();
  }

  private void throwExceptionIfDPoPProofMissing() {
    if (!dpopResult.exists()) {
      throw new DPoPProofInvalidException(
          "Original token was DPoP-bound, refresh requires DPoP proof");
    }
  }

  private void throwExceptionIfDPoPKeyMismatch() {
    if (!originalAccessToken.matchJwkThumbprint(dpopResult.jwkThumbprint())) {
      throw new DPoPProofInvalidException("DPoP proof key does not match original token binding");
    }
  }
}
