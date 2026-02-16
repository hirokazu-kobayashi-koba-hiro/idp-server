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

package org.idp.server.core.openid.oauth.verifier.base;

import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;
import org.idp.server.core.openid.oauth.type.oauth.ResponseType;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestVerifier;

/**
 * oauth2.0 base verifier
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749">RFC6749</a>
 */
public class OAuthRequestBaseVerifier implements AuthorizationRequestVerifier {

  @Override
  public AuthorizationProfile profile() {
    return AuthorizationProfile.OAUTH2;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    throwExceptionIfMissingResponseType(context);
    throwExceptionIfInvalidResponseType(context);
    throwExceptionIfUnSupportedResponseType(context);
    throwExceptionIfNotContainsValidScope(context);
  }

  /**
   * 3.1.1. Response Type REQUIRED.
   *
   * <p>The value MUST be one of "code" for requesting an authorization code as described by Section
   * 4.1.1, "token" for requesting an access token (implicit grant) as described by Section 4.2.1,
   * or a registered extension value as described by Section 8.4.
   *
   * <p>If an authorization request is missing the "response_type" parameter,
   *
   * @param context
   */
  void throwExceptionIfMissingResponseType(OAuthRequestContext context) {
    ResponseType responseType = context.responseType();
    if (responseType.isUndefined()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request", "response type is required in authorization request", context);
    }
  }

  /**
   * 3.1.1. Response Type REQUIRED.
   *
   * <p>if the response type is not understood, the authorization server MUST return an error
   * response as described in Section 4.1.2.1.
   *
   * @param context
   */
  void throwExceptionIfInvalidResponseType(OAuthRequestContext context) {
    ResponseType responseType = context.responseType();
    if (responseType.isUnknown()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "response type is unknown type (%s)",
              context.getParams(OAuthRequestKey.response_type)),
          context);
    }
  }

  /**
   * response_type values that this OP supports.
   *
   * <p>4.1.2.1. Error Response unauthorized_client The client is not authorized to request an
   * authorization code using this method.
   *
   * @param context
   * @see <a
   *     href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata">OpenID
   *     Provider Metadata</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2.1">4.1.2.1. Error
   *     Response</a>
   */
  void throwExceptionIfUnSupportedResponseType(OAuthRequestContext context) {
    ResponseType responseType = context.responseType();
    if (!context.isSupportedResponseTypeWithServer()) {
      throw new OAuthRedirectableBadRequestException(
          "unsupported_response_type",
          String.format(
              "authorization server is unsupported response_type (%s)", responseType.name()),
          context);
    }

    if (!context.isSupportedResponseTypeWithClient()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          String.format("client is unauthorized response_type (%s)", responseType.name()),
          context);
    }
  }

  /**
   * 3.3. Access Token Scope
   *
   * <p>If the client omits the scope parameter when requesting authorization, the authorization
   * server MUST either process the request using a pre-defined default value or fail the request
   * indicating an invalid scope. The authorization server SHOULD document its scope requirements
   * and default value (if defined).
   *
   * <p>invalid_scope The requested scope is invalid, unknown, or malformed.
   *
   * <p>When the authorization request uses a Request Object (JWT) and the scope claim is missing
   * from the JWT payload, the error code is "invalid_request_object" rather than "invalid_scope",
   * because the Request Object itself is malformed. This distinction is important for FAPI 1.0
   * Advanced conformance, where the scope claim is required in the signed Request Object.
   *
   * @param context
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.3">3.3. Access Token Scope</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2.1">invalid_scope</a>
   * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#RequestObject">OIDC Core
   *     Section 6.1 - Request Object</a>
   */
  void throwExceptionIfNotContainsValidScope(OAuthRequestContext context) {
    Scopes scopes = context.scopes();
    if (!scopes.exists()) {
      if (context.isRequestParameterPattern()
          && !context.joseContext().claims().contains("scope")) {
        throw new OAuthRedirectableBadRequestException(
            "invalid_request_object",
            "request object is invalid, scope claim must be included in the request object",
            context);
      }
      throw new OAuthRedirectableBadRequestException(
          "invalid_scope",
          String.format(
              "authorization request does not contains valid scope (%s)",
              context.getParams(OAuthRequestKey.scope)),
          context);
    }
  }
}
