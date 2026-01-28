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

package org.idp.server.core.openid.oauth.handler;

import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.exception.OAuthAuthorizeBadRequestException;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.io.OAuthAuthorizeResponse;
import org.idp.server.core.openid.oauth.io.OAuthAuthorizeStatus;
import org.idp.server.core.openid.oauth.response.AuthorizationErrorResponse;
import org.idp.server.core.openid.oauth.response.AuthorizationErrorResponseCreator;
import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.platform.log.LoggerWrapper;

public class OAuthAuthorizeErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthAuthorizeErrorHandler.class);

  public OAuthAuthorizeResponse handle(Exception exception) {

    if (exception instanceof OAuthBadRequestException badRequestException) {
      log.warn(
          "OAuth authorize failed: status=bad_request, error={}, description={}",
          badRequestException.error().value(),
          badRequestException.errorDescription().value());
      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.BAD_REQUEST,
          badRequestException.error().value(),
          badRequestException.errorDescription().value());
    }

    if (exception instanceof OAuthRedirectableBadRequestException redirectableBadRequestException) {
      AuthorizationErrorResponseCreator authorizationErrorResponseCreator =
          new AuthorizationErrorResponseCreator(redirectableBadRequestException);
      AuthorizationErrorResponse errorResponse = authorizationErrorResponseCreator.create();
      log.warn(
          "OAuth authorize failed: status=redirectable_bad_request, error={}, description={}",
          redirectableBadRequestException.error().value(),
          redirectableBadRequestException.errorDescription().value());
      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.REDIRECABLE_BAD_REQUEST, errorResponse);
    }

    if (exception instanceof OAuthAuthorizeBadRequestException badRequestException) {
      log.warn(
          "OAuth authorize failed: status=bad_request, error={}, description={}",
          badRequestException.error().value(),
          badRequestException.errorDescription().value());
      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.BAD_REQUEST,
          badRequestException.error().value(),
          badRequestException.errorDescription().value());
    }

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(
          "OAuth authorize failed: status=bad_request, error=invalid_request, description={}",
          exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.BAD_REQUEST, error.value(), errorDescription.value());
    }

    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(
          "OAuth authorize failed: status=bad_request, error=invalid_request, description={}",
          exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.BAD_REQUEST, error.value(), errorDescription.value());
    }

    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    log.error(
        "OAuth authorize failed: status=server_error, error={}", exception.getMessage(), exception);
    return new OAuthAuthorizeResponse(
        OAuthAuthorizeStatus.SERVER_ERROR, error.value(), errorDescription.value());
  }
}
