package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.handler.ciba.CibaRequestHandler;
import org.idp.server.handler.ciba.io.CibaRequest;
import org.idp.server.handler.ciba.io.CibaRequestResponse;
import org.idp.server.handler.ciba.io.CibaRequestStatus;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;

public class CibaApi {

  CibaRequestHandler cibaRequestHandler;
  Logger log = Logger.getLogger(CibaApi.class.getName());

  CibaApi(CibaRequestHandler cibaRequestHandler) {
    this.cibaRequestHandler = cibaRequestHandler;
  }

  public CibaRequestResponse request(CibaRequest request, CibaRequestDelegate delegate) {
    try {
      return cibaRequestHandler.handle(request, delegate);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new CibaRequestResponse(
          CibaRequestStatus.SERVER_ERROR,
          new BackchannelAuthenticationErrorResponse(
              new Error("server_error"), new ErrorDescription(exception.getMessage())));
    }
  }
}
