package org.idp.server;

import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.handler.ciba.CibaRequestErrorHandler;
import org.idp.server.handler.ciba.CibaRequestHandler;
import org.idp.server.handler.ciba.io.CibaRequest;
import org.idp.server.handler.ciba.io.CibaRequestResponse;

public class CibaApi {

  CibaRequestHandler cibaRequestHandler;
  CibaRequestErrorHandler errorHandler;

  CibaApi(CibaRequestHandler cibaRequestHandler) {
    this.cibaRequestHandler = cibaRequestHandler;
    this.errorHandler = new CibaRequestErrorHandler();
  }

  public CibaRequestResponse request(CibaRequest request, CibaRequestDelegate delegate) {
    try {
      return cibaRequestHandler.handle(request, delegate);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }
}
