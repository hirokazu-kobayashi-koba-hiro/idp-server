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

package org.idp.server.core.openid.oauth.response;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.type.extension.JarmPayload;
import org.idp.server.core.openid.oauth.type.extension.ResponseModeValue;
import org.idp.server.core.openid.oauth.type.oauth.RedirectUri;
import org.idp.server.core.openid.oauth.type.oauth.State;
import org.idp.server.core.openid.oauth.type.oauth.TokenIssuer;
import org.idp.server.core.openid.oauth.type.oidc.ResponseMode;

public class AuthorizationErrorResponseCreator
    implements RedirectUriDecidable, ResponseModeDecidable, JarmCreatable {

  OAuthRedirectableBadRequestException exception;

  public AuthorizationErrorResponseCreator(OAuthRedirectableBadRequestException exception) {
    this.exception = exception;
  }

  /**
   * Creates an authorization error response to redirect the user-agent back to the client.
   *
   * <p>JARM (JWT Secured Authorization Response Mode) wrapping is applied only when the client
   * explicitly requested a JWT response mode (jwt, query.jwt, fragment.jwt, form_post.jwt). Unlike
   * success responses, error responses must NOT use profile-based JARM auto-detection
   * (context.isJwtMode()), because the request itself may be invalid (e.g., response_type=code
   * without response_mode=jwt in FAPI Advanced). In such cases, the error must be returned as plain
   * query/fragment parameters so that the client can parse it.
   *
   * <p>FAPI 1.0 Advanced Final, Section 5.2.2 (clause 2): shall require the response_type value
   * code id_token, or the response_type value code in conjunction with the response_mode value jwt.
   * When this requirement is violated, the error response should use plain query parameters, not
   * JARM.
   *
   * @see <a
   *     href="https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server">FAPI
   *     1.0 Advanced Final Section 5.2.2</a>
   * @see <a href="https://openid.net/specs/oauth-v2-jarm-final.html">JARM specification</a>
   */
  public AuthorizationErrorResponse create() {
    OAuthRequestContext context = exception.oAuthRequestContext();
    RedirectUri redirectUri = context.redirectUri();
    TokenIssuer tokenIssuer = context.tokenIssuer();
    ResponseModeValue responseModeValue = context.responseModeValue();
    ResponseMode responseMode = context.responseMode();
    State state = context.state();
    AuthorizationErrorResponseBuilder builder =
        new AuthorizationErrorResponseBuilder(
                redirectUri, responseMode, responseModeValue, tokenIssuer)
            .add(state)
            .add(exception.error())
            .add(exception.errorDescription());
    if (context.responseMode().isJwtMode()) {
      AuthorizationErrorResponse errorResponse = builder.build();
      JarmPayload jarmPayload =
          createResponse(
              errorResponse, context.serverConfiguration(), context.clientConfiguration());
      builder.add(jarmPayload);
    }

    return builder.build();
  }
}
