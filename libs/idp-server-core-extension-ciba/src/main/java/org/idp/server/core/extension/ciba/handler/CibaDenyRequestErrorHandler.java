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
import org.idp.server.core.extension.ciba.handler.io.CibaDenyResponse;
import org.idp.server.core.extension.ciba.handler.io.CibaDenyStatus;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.platform.log.LoggerWrapper;

public class CibaDenyRequestErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(CibaDenyRequestErrorHandler.class);

  public CibaDenyResponse handle(Exception exception) {
    if (exception instanceof CibaAuthorizeBadRequestException badRequest) {
      log.warn(exception.getMessage());
      return new CibaDenyResponse(
          CibaDenyStatus.BAD_REQUEST, badRequest.error(), badRequest.errorDescription());
    }

    if (exception instanceof CibaGrantNotFoundException cibaGrantNotFoundException) {
      log.warn(exception.getMessage());
      return new CibaDenyResponse(
          CibaDenyStatus.BAD_REQUEST,
          new Error("invalid_request"),
          new ErrorDescription(cibaGrantNotFoundException.getMessage()));
    }

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new CibaDenyResponse(
          CibaDenyStatus.BAD_REQUEST,
          new Error("invalid_client"),
          new ErrorDescription(exception.getMessage()));
    }

    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new CibaDenyResponse(
          CibaDenyStatus.BAD_REQUEST,
          new Error("invalid_request"),
          new ErrorDescription(exception.getMessage()));
    }

    log.error(exception.getMessage(), exception);
    return new CibaDenyResponse(
        CibaDenyStatus.SERVER_ERROR,
        new Error("server_error"),
        new ErrorDescription(exception.getMessage()));
  }
}
