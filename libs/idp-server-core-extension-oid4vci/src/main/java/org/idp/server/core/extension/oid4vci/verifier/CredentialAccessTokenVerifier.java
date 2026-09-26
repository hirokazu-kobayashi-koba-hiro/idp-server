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
package org.idp.server.core.extension.oid4vci.verifier;

import org.idp.server.core.extension.oid4vci.io.CredentialRequest;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.core.openid.token.verifier.AccessTokenSenderConstraintVerifier;
import org.idp.server.platform.date.SystemDateTime;

/**
 * Checks the access token presented at the Credential Endpoint (OpenID4VCI 1.0 Section 8.3.1.1:
 * errors are those of RFC 6750 Section 3).
 *
 * <p>The token has to be a user's: a credential describes its holder, and a token without a subject
 * has nobody to describe.
 */
public class CredentialAccessTokenVerifier {

  OAuthToken oAuthToken;
  CredentialRequest request;

  public CredentialAccessTokenVerifier(OAuthToken oAuthToken, CredentialRequest request) {
    this.oAuthToken = oAuthToken;
    this.request = request;
  }

  public void verify() {
    throwExceptionIfNotFoundToken();
    throwExceptionIfNoSubject();
    new AccessTokenSenderConstraintVerifier(
            oAuthToken,
            request.toClientCert(),
            request.dpopProof(),
            request.httpMethod(),
            request.httpUri())
        .verify();
  }

  void throwExceptionIfNotFoundToken() {
    if (!oAuthToken.exists()) {
      throw new TokenInvalidException("not found token");
    }
    if (oAuthToken.isExpiredAccessToken(SystemDateTime.now())) {
      throw new TokenInvalidException("token is expired");
    }
  }

  void throwExceptionIfNoSubject() {
    if (!oAuthToken.hasSubject()) {
      throw new TokenInvalidException(
          "token has no subject; a credential is issued to the End-User who authorized it");
    }
  }
}
