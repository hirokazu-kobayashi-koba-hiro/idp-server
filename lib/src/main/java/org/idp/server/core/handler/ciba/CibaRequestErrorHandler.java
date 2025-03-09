package org.idp.server.core.handler.ciba;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.handler.ciba.io.CibaRequestResponse;
import org.idp.server.core.handler.ciba.io.CibaRequestStatus;
import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;

public class CibaRequestErrorHandler {

  Logger log = Logger.getLogger(CibaRequestErrorHandler.class.getName());

  public CibaRequestResponse handle(Exception exception) {
    if (exception instanceof BackchannelAuthenticationBadRequestException badRequest) {
      log.log(Level.WARNING, exception.getMessage());
      return new CibaRequestResponse(
          CibaRequestStatus.BAD_REQUEST,
          new BackchannelAuthenticationErrorResponse(
              badRequest.error(), badRequest.errorDescription()));
    }
    if (exception instanceof ClientUnAuthorizedException) {
      log.log(Level.WARNING, exception.getMessage());
      return new CibaRequestResponse(
          CibaRequestStatus.UNAUTHORIZE,
          new BackchannelAuthenticationErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }

    log.log(Level.SEVERE, exception.getMessage(), exception);
    return new CibaRequestResponse(
        CibaRequestStatus.SERVER_ERROR,
        new BackchannelAuthenticationErrorResponse(
            new Error("server_error"), new ErrorDescription(exception.getMessage())));
  }
}
