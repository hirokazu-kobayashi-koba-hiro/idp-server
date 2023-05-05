package org.idp.server;

import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.handler.ciba.CibaAuthorizeHandler;
import org.idp.server.handler.ciba.CibaDenyHandler;
import org.idp.server.handler.ciba.CibaRequestErrorHandler;
import org.idp.server.handler.ciba.CibaRequestHandler;
import org.idp.server.handler.ciba.io.*;

public class CibaApi {

  CibaRequestHandler cibaRequestHandler;
  CibaAuthorizeHandler cibaAuthorizeHandler;
  CibaDenyHandler cibaDenyHandler;
  CibaRequestErrorHandler errorHandler;

  CibaApi(
      CibaRequestHandler cibaRequestHandler,
      CibaAuthorizeHandler cibaAuthorizeHandler,
      CibaDenyHandler cibaDenyHandler) {
    this.cibaRequestHandler = cibaRequestHandler;
    this.cibaAuthorizeHandler = cibaAuthorizeHandler;
    this.cibaDenyHandler = cibaDenyHandler;
    this.errorHandler = new CibaRequestErrorHandler();
  }

  public CibaRequestResponse request(CibaRequest request, CibaRequestDelegate delegate) {
    try {
      return cibaRequestHandler.handle(request, delegate);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }

  public CibaAuthorizeResponse authorize(CibaAuthorizeRequest request) {
    try {
      return cibaAuthorizeHandler.handle(request);
    } catch (Exception exception) {
      return new CibaAuthorizeResponse(CibaAuthorizeStatus.SERVER_ERROR);
    }
  }

  public CibaDenyResponse deny(CibaDenyRequest request) {
    try {
      return cibaDenyHandler.handle(request);
    } catch (Exception exception) {
      return new CibaDenyResponse(CibaDenyStatus.SERVER_ERROR);
    }
  }
}
