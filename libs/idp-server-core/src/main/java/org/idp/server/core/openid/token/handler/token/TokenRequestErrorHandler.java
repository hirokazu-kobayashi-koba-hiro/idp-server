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

package org.idp.server.core.openid.token.handler.token;

import static org.idp.server.core.openid.token.handler.token.io.TokenRequestStatus.*;

import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.core.openid.token.TokenErrorResponse;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.exception.TokenUnSupportedGrantException;
import org.idp.server.core.openid.token.handler.token.io.TokenRequestResponse;
import org.idp.server.platform.log.LoggerWrapper;

public class TokenRequestErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(TokenRequestErrorHandler.class);

  public TokenRequestResponse handle(Exception exception) {
    log.trace(
        "Token request error handling started: exception_type={}",
        exception.getClass().getSimpleName());

    if (exception instanceof TokenBadRequestException badRequest) {
      log.warn(
          "Token request validation failed: error={}, description={}",
          badRequest.error().value(),
          badRequest.errorDescription().value());
      return new TokenRequestResponse(
          BAD_REQUEST, new TokenErrorResponse(badRequest.error(), badRequest.errorDescription()));
    }

    if (exception instanceof TokenUnSupportedGrantException badRequest) {
      log.warn(
          "Unsupported grant type: error={}, description={}",
          badRequest.error().value(),
          badRequest.errorDescription().value());
      return new TokenRequestResponse(
          BAD_REQUEST, new TokenErrorResponse(badRequest.error(), badRequest.errorDescription()));
    }

    if (exception instanceof ClientUnAuthorizedException) {
      log.warn("Client authentication failed: reason={}", exception.getMessage());
      return new TokenRequestResponse(
          UNAUTHORIZE,
          new TokenErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getLocalizedMessage())));
    }

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn("Client configuration not found: error={}", exception.getMessage());
      return new TokenRequestResponse(
          UNAUTHORIZE,
          new TokenErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }

    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn("Server configuration not found: error={}", exception.getMessage());
      return new TokenRequestResponse(
          BAD_REQUEST,
          new TokenErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }

    log.error("Token request server error: error={}", exception.getMessage(), exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    TokenErrorResponse tokenErrorResponse = new TokenErrorResponse(error, errorDescription);
    return new TokenRequestResponse(SERVER_ERROR, tokenErrorResponse);
  }
}
