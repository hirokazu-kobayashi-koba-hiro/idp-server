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

package org.idp.server.core.openid.token.handler.tokenrevocation;

import static org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationRequestStatus.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.openid.token.tokenrevocation.exception.TokenRevocationBadRequestException;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Error handler for token revocation requests.
 *
 * <p>Maps exceptions to appropriate HTTP status codes and error responses according to RFC 7009
 * (OAuth 2.0 Token Revocation).
 *
 * <p>Error Response Mapping:
 *
 * <ul>
 *   <li>TokenRevocationBadRequestException → 400 Bad Request (invalid_request)
 *   <li>ClientUnAuthorizedException → 401 Unauthorized (invalid_client)
 *   <li>ClientConfigurationNotFoundException → 400 Bad Request (invalid_client)
 *   <li>ServerConfigurationNotFoundException → 400 Bad Request (invalid_client)
 *   <li>Other exceptions → 500 Internal Server Error (server_error)
 * </ul>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7009#section-2.2">RFC 7009 Section 2.2 - Token
 *     Revocation Response</a>
 */
public class TokenRevocationErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(TokenRevocationErrorHandler.class);

  /**
   * Handles exceptions and returns appropriate error response.
   *
   * @param exception the exception to handle
   * @return TokenRevocationResponse with appropriate status code and error details
   */
  public TokenRevocationResponse handle(Exception exception) {
    // RFC 7009: invalid_request (400)
    if (exception instanceof TokenRevocationBadRequestException badRequest) {
      log.warn(exception.getMessage());

      Map<String, Object> contents = new HashMap<>();
      contents.put("error", badRequest.error().value());
      contents.put("error_description", badRequest.errorDescription().value());

      return new TokenRevocationResponse(BAD_REQUEST, new OAuthToken(), contents);
    }

    // RFC 7009: invalid_client (401)
    if (exception instanceof ClientUnAuthorizedException) {
      log.warn(exception.getMessage());

      Map<String, Object> contents = new HashMap<>();
      contents.put("error", "invalid_client");
      contents.put("error_description", exception.getMessage());

      return new TokenRevocationResponse(UNAUTHORIZED, new OAuthToken(), contents);
    }

    // Configuration errors (400)
    if (exception instanceof ClientConfigurationNotFoundException
        || exception instanceof ServerConfigurationNotFoundException) {
      log.warn(exception.getMessage());

      Map<String, Object> contents = new HashMap<>();
      contents.put("error", "invalid_client");
      contents.put("error_description", exception.getMessage());

      return new TokenRevocationResponse(BAD_REQUEST, new OAuthToken(), contents);
    }

    // Server error (500)
    log.error(exception.getMessage(), exception);
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", "server_error");
    contents.put("error_description", exception.getMessage());

    return new TokenRevocationResponse(SERVER_ERROR, new OAuthToken(), contents);
  }
}
