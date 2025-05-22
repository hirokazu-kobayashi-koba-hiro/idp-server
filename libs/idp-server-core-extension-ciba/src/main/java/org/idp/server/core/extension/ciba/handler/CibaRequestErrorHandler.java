/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.handler;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.extension.ciba.handler.io.CibaRequestResult;
import org.idp.server.core.extension.ciba.handler.io.CibaRequestStatus;
import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.oidc.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.platform.log.LoggerWrapper;

public class CibaRequestErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(CibaRequestErrorHandler.class);

  public CibaRequestResult handle(Exception exception) {
    if (exception instanceof BackchannelAuthenticationBadRequestException badRequest) {
      log.warn(exception.getMessage());
      return new CibaRequestResult(
          CibaRequestStatus.BAD_REQUEST,
          new BackchannelAuthenticationErrorResponse(
              badRequest.error(), badRequest.errorDescription()));
    }
    if (exception instanceof ClientUnAuthorizedException) {
      log.warn(exception.getMessage());
      return new CibaRequestResult(
          CibaRequestStatus.UNAUTHORIZE,
          new BackchannelAuthenticationErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }

    log.error(exception.getMessage(), exception);
    return new CibaRequestResult(
        CibaRequestStatus.SERVER_ERROR,
        new BackchannelAuthenticationErrorResponse(
            new Error("server_error"), new ErrorDescription(exception.getMessage())));
  }
}
