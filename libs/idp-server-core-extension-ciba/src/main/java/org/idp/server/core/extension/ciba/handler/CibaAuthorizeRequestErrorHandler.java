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

package org.idp.server.core.extension.ciba.handler;

import org.idp.server.core.extension.ciba.exception.CibaAuthorizeBadRequestException;
import org.idp.server.core.extension.ciba.exception.CibaGrantNotFoundException;
import org.idp.server.core.extension.ciba.handler.io.CibaAuthorizeResponse;
import org.idp.server.core.extension.ciba.handler.io.CibaAuthorizeStatus;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.platform.log.LoggerWrapper;

public class CibaAuthorizeRequestErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(CibaAuthorizeRequestErrorHandler.class);

  public CibaAuthorizeResponse handle(Exception exception) {
    if (exception instanceof CibaAuthorizeBadRequestException badRequest) {
      log.warn(
          "CIBA authorize failed: status=bad_request, error={}, description={}",
          badRequest.error().value(),
          badRequest.errorDescription().value());
      return new CibaAuthorizeResponse(
          CibaAuthorizeStatus.BAD_REQUEST, badRequest.error(), badRequest.errorDescription());
    }

    if (exception instanceof CibaGrantNotFoundException cibaGrantNotFoundException) {
      log.warn(
          "CIBA authorize failed: status=bad_request, error=invalid_request, description={}",
          cibaGrantNotFoundException.getMessage());
      return new CibaAuthorizeResponse(
          CibaAuthorizeStatus.BAD_REQUEST,
          new Error("invalid_request"),
          new ErrorDescription(cibaGrantNotFoundException.getMessage()));
    }

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(
          "CIBA authorize failed: status=bad_request, error=invalid_client, description={}",
          exception.getMessage());
      return new CibaAuthorizeResponse(
          CibaAuthorizeStatus.BAD_REQUEST,
          new Error("invalid_client"),
          new ErrorDescription(exception.getMessage()));
    }

    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(
          "CIBA authorize failed: status=bad_request, error=invalid_request, description={}",
          exception.getMessage());
      return new CibaAuthorizeResponse(
          CibaAuthorizeStatus.BAD_REQUEST,
          new Error("invalid_request"),
          new ErrorDescription(exception.getMessage()));
    }

    log.error(
        "CIBA authorize failed: status=server_error, error={}", exception.getMessage(), exception);
    return new CibaAuthorizeResponse(
        CibaAuthorizeStatus.SERVER_ERROR,
        new Error("server_error"),
        new ErrorDescription(exception.getMessage()));
  }
}
