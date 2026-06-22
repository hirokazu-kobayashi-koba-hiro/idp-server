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

package org.idp.server.core.openid.extension.pkce;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.platform.log.LoggerWrapper;

public class PkceVerifier implements AuthorizationRequestExtensionVerifier {

  LoggerWrapper log = LoggerWrapper.getLogger(PkceVerifier.class);

  @Override
  public boolean shouldVerify(OAuthRequestContext context) {
    if (context.isPckeRequest()) {
      return true;
    }
    // Issue #1523: require_pkce applies only to code-issuing flows (code/hybrid).
    // PKCE has no meaning for implicit flows, so they are out of scope.
    if (!context.isAuthorizationCodeFlowOrHybridFlow()) {
      return false;
    }
    return context.clientConfiguration().isRequirePkce();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    log.debug("PKCE verification start");
    throwExceptionIfRequirePkceButMissing(context);
    log.debug("PKCE verification end");
  }

  /**
   * Issue #1523: when the client has {@code require_pkce} enabled, an authorization request that
   * issues a code (code/hybrid flow) MUST contain a {@code code_challenge} with {@code
   * code_challenge_method=S256}. Implicit flows are out of scope since PKCE protects the code
   * exchange at the token endpoint.
   */
  void throwExceptionIfRequirePkceButMissing(OAuthRequestContext context) {
    if (!context.clientConfiguration().isRequirePkce()) {
      return;
    }
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (!authorizationRequest.hasCodeChallenge()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request", "PKCE is required for this client", context);
    }
    if (!authorizationRequest.codeChallengeMethod().isS256()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "PKCE is required for this client and code_challenge_method must be S256",
          context);
    }
  }
}
