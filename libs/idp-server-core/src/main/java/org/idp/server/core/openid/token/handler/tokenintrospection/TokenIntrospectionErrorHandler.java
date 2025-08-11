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

package org.idp.server.core.openid.token.handler.tokenintrospection;

import static org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenCertificationBindingInvalidException;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInsufficientScopeException;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenIntrospectionBadRequestException;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.platform.log.LoggerWrapper;

public class TokenIntrospectionErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(TokenIntrospectionErrorHandler.class);

  public TokenIntrospectionResponse handle(Exception exception) {
    if (exception instanceof TokenIntrospectionBadRequestException badRequest) {
      log.warn(exception.getMessage());

      Map<String, Object> contents = new HashMap<>();
      contents.put("active", false);
      contents.put("error", badRequest.error().value());
      contents.put("error_description", badRequest.errorDescription().value());
      contents.put("status_code", 400);

      return new TokenIntrospectionResponse(BAD_REQUEST, contents);
    }

    if (exception instanceof TokenInvalidException tokenInvalid) {
      log.warn(exception.getMessage());

      Map<String, Object> contents = new HashMap<>();
      contents.put("active", false);
      contents.put("error", "invalid_token");
      contents.put("error_description", tokenInvalid.getMessage());
      contents.put("status_code", 401);

      return new TokenIntrospectionResponse(INVALID_TOKEN, contents);
    }

    if (exception instanceof TokenCertificationBindingInvalidException bindingInvalidException) {
      log.warn(exception.getMessage());

      Map<String, Object> contents = new HashMap<>();
      contents.put("active", false);
      contents.put("error", "invalid_token");
      contents.put("error_description", bindingInvalidException.getMessage());
      contents.put("status_code", 401);

      return new TokenIntrospectionResponse(INVALID_CLIENT_CERT, contents);
    }

    if (exception instanceof TokenInsufficientScopeException insufficientScopeException) {
      log.warn(exception.getMessage());

      Map<String, Object> contents = new HashMap<>();
      contents.put("active", false);
      contents.put("error", "insufficient_scope");
      contents.put("error_description", insufficientScopeException.getMessage());
      contents.put("status_code", 403);

      return new TokenIntrospectionResponse(INSUFFICIENT_SCOPE, contents);
    }

    if (exception instanceof ClientUnAuthorizedException) {
      Map<String, Object> contents = new HashMap<>();
      contents.put("active", false);
      contents.put("error", "invalid_client");
      contents.put("error_description", exception.getMessage());
      contents.put("status_code", 400);

      return new TokenIntrospectionResponse(BAD_REQUEST, contents);
    }

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(exception.getMessage());

      Map<String, Object> contents = new HashMap<>();
      contents.put("active", false);
      contents.put("error", "invalid_client");
      contents.put("error_description", exception.getMessage());
      contents.put("status_code", 400);

      return new TokenIntrospectionResponse(BAD_REQUEST, contents);
    }

    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      Map<String, Object> contents = new HashMap<>();
      contents.put("active", false);
      contents.put("error", "invalid_client");
      contents.put("error_description", exception.getMessage());
      contents.put("status_code", 400);

      return new TokenIntrospectionResponse(BAD_REQUEST, contents);
    }

    log.error(exception.getMessage(), exception);
    Map<String, Object> contents = new HashMap<>();
    contents.put("active", false);
    contents.put("error", "server_error");
    contents.put("error_description", exception.getMessage());
    contents.put("status_code", 500);

    return new TokenIntrospectionResponse(SERVER_ERROR, contents);
  }
}
