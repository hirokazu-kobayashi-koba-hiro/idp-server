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

import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.dpop.DPoPProofInvalidException;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifiedResult;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;

/**
 * Verifies DPoP behavior on the refresh token grant per RFC 9449 Section 5.
 *
 * <p>Per RFC 9449 §5:
 *
 * <ul>
 *   <li><b>Public clients</b>: refresh tokens are bound to the DPoP public key. The refresh request
 *       MUST present a DPoP proof signed with the same key as the original binding.
 *   <li><b>Confidential clients</b>: refresh tokens are NOT bound to the DPoP public key. They are
 *       sender-constrained by client authentication (private_key_jwt / mTLS / client_secret_*).
 *       Therefore the DPoP key MAY rotate between the initial issuance and any refresh, allowing
 *       client key rotation without invalidating refresh tokens.
 * </ul>
 *
 * <p>If the access token was DPoP-bound, the refresh request still MUST include a DPoP proof so
 * that the new access token can be re-bound (to the same key for public clients, or to the new key
 * for confidential clients).
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-5">RFC 9449 Section 5</a>
 */
public class RefreshTokenDPoPBindingVerifier {

  AccessToken originalAccessToken;
  DPoPProofVerifiedResult dpopResult;
  ClientConfiguration clientConfiguration;

  public RefreshTokenDPoPBindingVerifier(
      AccessToken originalAccessToken,
      DPoPProofVerifiedResult dpopResult,
      ClientConfiguration clientConfiguration) {
    this.originalAccessToken = originalAccessToken;
    this.dpopResult = dpopResult;
    this.clientConfiguration = clientConfiguration;
  }

  public void verify() {
    if (!originalAccessToken.hasDPoPBinding()) {
      return;
    }

    throwExceptionIfDPoPProofMissing();

    if (isPublicClient()) {
      throwExceptionIfDPoPKeyMismatch();
    }
  }

  private boolean isPublicClient() {
    return clientConfiguration.clientAuthenticationType().isNone();
  }

  /**
   * A missing DPoP header is "the request is missing a required parameter" per RFC 6749 §5.2, so
   * surface it as {@code invalid_request} rather than {@code invalid_dpop_proof}. The latter is
   * reserved by RFC 9449 §5.2 for cases where a DPoP proof is present but cannot be verified.
   */
  private void throwExceptionIfDPoPProofMissing() {
    if (!dpopResult.exists()) {
      throw new TokenBadRequestException(
          "invalid_request", "Original token was DPoP-bound, refresh requires DPoP proof");
    }
  }

  /**
   * Public-client refresh keys are bound to the original DPoP public key (RFC 9449 §5). Refusing a
   * mismatched key here prevents a stolen refresh token from being used with a different key pair.
   */
  private void throwExceptionIfDPoPKeyMismatch() {
    if (!originalAccessToken.matchJwkThumbprint(dpopResult.jwkThumbprint())) {
      throw new DPoPProofInvalidException("DPoP proof key does not match original token binding");
    }
  }
}
