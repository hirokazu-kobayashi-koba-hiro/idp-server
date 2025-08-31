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

package org.idp.server.core.extension.ciba.token;

import java.time.LocalDateTime;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.platform.date.SystemDateTime;

/**
 * CibaGrantVerifier
 *
 * <p>If the Token Request is invalid or unauthorized, the OpenID Provider constructs an error
 * response according to Section 3.1.3.4 Token Error Response of [OpenID.Core]. In addition to the
 * error codes defined in Section 5.2 of [RFC6749], the following error codes defined in the OAuth
 * Device Flow are also applicable:
 *
 * @see <a
 *     href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.11">Token
 *     Error Response</a>
 */
public class CibaGrantVerifier {

  TokenRequestContext context;
  BackchannelAuthenticationRequest request;
  CibaGrant cibaGrant;

  public CibaGrantVerifier(
      TokenRequestContext context, BackchannelAuthenticationRequest request, CibaGrant cibaGrant) {
    this.context = context;
    this.request = request;
    this.cibaGrant = cibaGrant;
  }

  public void verify() {
    throwExceptionIfInvalidAuthReqId();
    throwExceptionIfPushMode();
    throwExceptionIfExpired();
    throwExceptionIfAuthorizedPending();
    throwExceptionIfAccessDenied();
  }

  /**
   * invalid_grant
   *
   * <p>If the auth_req_id is invalid or was issued to another Client, an invalid_grant error MUST
   * be returned as described in Section 5.2 of [RFC6749].
   */
  void throwExceptionIfInvalidAuthReqId() {
    if (!cibaGrant.exists()) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format(
              "The auth_req_id is invalid or was issued to another Client. (%s)",
              context.requestedClientId().value()));
    }
    if (!cibaGrant.isGrantedClient(context.clientIdentifier())) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format(
              "The auth_req_id is invalid or was issued to another Client. (%s)",
              context.requestedClientId().value()));
    }
  }

  /**
   * unauthorized_client
   *
   * <p>If the Client is registered to use the Push Mode then it MUST NOT call the Token Endpoint
   * with the CIBA Grant Type and the following error is returned.
   */
  void throwExceptionIfPushMode() {
    if (context.isPushMode()) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "backchannel delivery mode is push. token request must not allowed");
    }
  }

  /**
   * expired_token
   *
   * <p>The auth_req_id has expired. The Client will need to make a new Authentication Request.
   */
  void throwExceptionIfExpired() {
    LocalDateTime now = SystemDateTime.now();
    if (cibaGrant.isExpire(now)) {
      throw new TokenBadRequestException(
          "expired_token",
          "The auth_req_id has expired. The Client will need to make a new Authentication Request.");
    }
  }

  /**
   * authorization_pending
   *
   * <p>The authorization request is still pending as the end-user hasn't yet been authenticated.
   */
  void throwExceptionIfAuthorizedPending() {
    if (cibaGrant.isAuthorizationPending()) {
      throw new TokenBadRequestException(
          "authorization_pending",
          "The authorization request is still pending as the end-user hasn't yet been authenticated.");
    }
  }

  /**
   * access_denied
   *
   * <p>The end-user denied the authorization request.
   */
  void throwExceptionIfAccessDenied() {
    if (cibaGrant.isAccessDenied()) {
      throw new TokenBadRequestException(
          "access_denied", "The end-user denied the authorization request.");
    }
  }
}
