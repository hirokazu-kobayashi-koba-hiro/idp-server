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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.io.OAuthPushedRequestResponse;
import org.idp.server.core.openid.oauth.io.OAuthPushedRequestStatus;
import org.idp.server.core.openid.oauth.io.OAuthRequestResponse;
import org.idp.server.core.openid.oauth.io.OAuthRequestStatus;
import org.idp.server.core.openid.oauth.response.AuthorizationErrorResponse;
import org.idp.server.core.openid.oauth.response.AuthorizationErrorResponseCreator;
import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.core.openid.oauth.view.OAuthViewUrlResolver;
import org.idp.server.platform.log.LoggerWrapper;

public class OAuthRequestErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthRequestErrorHandler.class);

  public OAuthPushedRequestResponse handlePushedRequest(Exception exception) {
    if (exception instanceof OAuthBadRequestException badRequestException) {
      log.warn(exception.getMessage());
      Map<String, Object> response = new HashMap<>();
      response.put("error", badRequestException.error().value());
      response.put("error_description", badRequestException.errorDescription().value());
      return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.BAD_REQUEST, response);
    }

    if (exception instanceof OAuthRedirectableBadRequestException redirectableBadRequestException) {
      log.warn(redirectableBadRequestException.getMessage());
      Map<String, Object> response = new HashMap<>();
      response.put("error", redirectableBadRequestException.error().value());
      response.put("error_description", redirectableBadRequestException.errorDescription().value());
      return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.BAD_REQUEST, response);
    }

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn("not found client configuration");
      log.warn(exception.getMessage());
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", exception.getMessage());
      return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.BAD_REQUEST, response);
    }

    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn("not found authorization server configuration");
      log.warn(exception.getMessage());
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", exception.getMessage());
      return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.BAD_REQUEST, response);
    }

    log.error(exception.getMessage(), exception);
    Map<String, Object> response = new HashMap<>();
    response.put("error", "server_error");
    response.put("error_description", exception.getMessage());
    return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.SERVER_ERROR, response);
  }

  public OAuthRequestResponse handle(Exception exception) {
    if (exception instanceof OAuthBadRequestException badRequestException) {
      log.warn(exception.getMessage());
      String frontUrl =
          OAuthViewUrlResolver.resolveError(
              badRequestException.tenant(),
              badRequestException.error(),
              badRequestException.errorDescription());
      return new OAuthRequestResponse(
          OAuthRequestStatus.BAD_REQUEST,
          frontUrl,
          badRequestException.error(),
          badRequestException.errorDescription());
    }

    if (exception instanceof OAuthRedirectableBadRequestException redirectableBadRequestException) {
      AuthorizationErrorResponseCreator authorizationErrorResponseCreator =
          new AuthorizationErrorResponseCreator(redirectableBadRequestException);
      AuthorizationErrorResponse errorResponse = authorizationErrorResponseCreator.create();
      log.warn(redirectableBadRequestException.getMessage());
      return new OAuthRequestResponse(OAuthRequestStatus.REDIRECABLE_BAD_REQUEST, errorResponse);
    }

    if (exception
        instanceof ClientConfigurationNotFoundException clientConfigurationNotFoundException) {
      log.warn("not found configuration");
      log.warn(exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      String frontUrl =
          OAuthViewUrlResolver.resolveError(
              clientConfigurationNotFoundException.tenant(), error, errorDescription);
      return new OAuthRequestResponse(
          OAuthRequestStatus.BAD_REQUEST, frontUrl, error, errorDescription);
    }

    if (exception
        instanceof ServerConfigurationNotFoundException serverConfigurationNotFoundException) {
      log.warn("not found configuration");
      log.warn(exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      String frontUrl =
          OAuthViewUrlResolver.resolveError(
              serverConfigurationNotFoundException.tenant(), error, errorDescription);
      return new OAuthRequestResponse(
          OAuthRequestStatus.BAD_REQUEST, frontUrl, error, errorDescription);
    }

    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    log.error(exception.getMessage(), exception);
    return new OAuthRequestResponse(OAuthRequestStatus.SERVER_ERROR, "", error, errorDescription);
  }
}
