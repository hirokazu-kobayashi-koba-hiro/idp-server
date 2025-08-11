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

package org.idp.server.core.openid.userinfo.handler;

import org.idp.server.core.openid.identity.exception.UserNotFoundException;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.core.openid.userinfo.UserinfoErrorResponse;
import org.idp.server.core.openid.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.core.openid.userinfo.handler.io.UserinfoRequestStatus;
import org.idp.server.platform.log.LoggerWrapper;

public class UserinfoErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(UserinfoErrorHandler.class);

  public UserinfoRequestResponse handle(Exception exception) {
    if (exception instanceof TokenInvalidException) {
      log.info(exception.getMessage());
      return new UserinfoRequestResponse(
          UserinfoRequestStatus.UNAUTHORIZE,
          new UserinfoErrorResponse(
              new Error("invalid_token"), new ErrorDescription(exception.getMessage())));
    }

    if (exception instanceof UserNotFoundException) {
      log.warn(exception.getMessage());
      return new UserinfoRequestResponse(
          UserinfoRequestStatus.UNAUTHORIZE,
          new UserinfoErrorResponse(
              new Error("invalid_token"), new ErrorDescription(exception.getMessage())));
    }

    if (exception instanceof ClientUnAuthorizedException) {
      log.warn(exception.getMessage());
      return new UserinfoRequestResponse(
          UserinfoRequestStatus.UNAUTHORIZE,
          new UserinfoErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getLocalizedMessage())));
    }

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new UserinfoRequestResponse(
          UserinfoRequestStatus.UNAUTHORIZE,
          new UserinfoErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }

    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new UserinfoRequestResponse(
          UserinfoRequestStatus.BAD_REQUEST,
          new UserinfoErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }

    log.error(exception.getMessage(), exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    UserinfoErrorResponse userinfoErrorResponse =
        new UserinfoErrorResponse(error, errorDescription);
    return new UserinfoRequestResponse(UserinfoRequestStatus.SERVER_ERROR, userinfoErrorResponse);
  }
}
