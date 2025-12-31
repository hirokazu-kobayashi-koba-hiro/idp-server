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

import org.idp.server.core.openid.grant_management.grant.AuthorizationCodeGrant;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.platform.date.SystemDateTime;

public class AuthorizationCodeGrantBaseVerifier {

  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant) {
    throwExceptionIfNotFoundAuthorizationCode(
        tokenRequestContext, authorizationRequest, authorizationCodeGrant);
    throwExceptionIfExpiredAuthorizationCode(authorizationCodeGrant);
    throwExceptionIfUnMatchRedirectUri(tokenRequestContext, authorizationRequest);
  }

  /**
   * 4.1.3. Access Token Request verification
   *
   * <p>ensure that the authorization code was issued to the authenticated confidential client, or
   * if the client is public, ensure that the code was issued to "client_id" in the request,
   *
   * <p>verify that the authorization code is valid
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.3">4.1.3. Access Token
   *     Request</a>
   */
  void throwExceptionIfNotFoundAuthorizationCode(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant) {
    if (!authorizationCodeGrant.exists()) {
      throw new TokenBadRequestException("invalid_grant", "not found authorization code.");
    }
    if (!authorizationRequest.exists()) {
      throw new TokenBadRequestException("invalid_grant", "not found authorization code.");
    }
    if (!authorizationCodeGrant.isGrantedClient(tokenRequestContext.clientIdentifier())) {
      throw new TokenBadRequestException("invalid_grant", "not found authorization code.");
    }
  }

  /**
   * 5.2. Error Response invalid_grant
   *
   * <p>The provided authorization grant (e.g., authorization code, resource owner credentials) or
   * refresh token is invalid, expired, revoked, does not match the redirection URI used in the
   * authorization request, or was issued to another client.
   *
   * <p>ensure that the authorization code has not expired
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">5.2. Error Response</a>
   */
  void throwExceptionIfExpiredAuthorizationCode(AuthorizationCodeGrant authorizationCodeGrant) {
    if (authorizationCodeGrant.isExpire(SystemDateTime.now())) {
      throw new TokenBadRequestException("invalid_grant", "authorization code is expired");
    }
  }

  /**
   * 4.1.3. Access Token Request verification
   *
   * <p>ensure that the "redirect_uri" parameter is present if the "redirect_uri" parameter was
   * included in the initial authorization request as described in Section 4.1.1, and if included
   * ensure that their values are identical.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.3">4.1.3. Access Token
   *     Request</a>
   */
  void throwExceptionIfUnMatchRedirectUri(
      TokenRequestContext tokenRequestContext, AuthorizationRequest authorizationRequest) {
    if (!authorizationRequest.hasRedirectUri()) {
      return;
    }
    if (!authorizationRequest.redirectUri().equals(tokenRequestContext.redirectUri())) {
      throw new TokenBadRequestException(
          String.format(
              "token request redirect_uri does not equals to authorization request redirect_uri (%s)",
              tokenRequestContext.redirectUri().value()));
    }
  }
}
