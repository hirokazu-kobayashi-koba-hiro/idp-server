package org.idp.server.core.ciba.handler;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.ciba.handler.io.CibaRequestResult;
import org.idp.server.core.ciba.handler.io.CibaRequestStatus;
import org.idp.server.core.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.oidc.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;

public class CibaRequestErrorHandler {

  Logger log = Logger.getLogger(CibaRequestErrorHandler.class.getName());

  public CibaRequestResult handle(Exception exception) {
    if (exception instanceof BackchannelAuthenticationBadRequestException badRequest) {
      log.log(Level.WARNING, exception.getMessage());
      return new CibaRequestResult(
          CibaRequestStatus.BAD_REQUEST,
          new BackchannelAuthenticationErrorResponse(
              badRequest.error(), badRequest.errorDescription()));
    }
    if (exception instanceof ClientUnAuthorizedException) {
      log.log(Level.WARNING, exception.getMessage());
      return new CibaRequestResult(
          CibaRequestStatus.UNAUTHORIZE,
          new BackchannelAuthenticationErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }

    log.log(Level.SEVERE, exception.getMessage(), exception);
    return new CibaRequestResult(
        CibaRequestStatus.SERVER_ERROR,
        new BackchannelAuthenticationErrorResponse(
            new Error("server_error"), new ErrorDescription(exception.getMessage())));
  }
}
