package org.idp.server.core.api;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.ciba.CibaRequestDelegate;
import org.idp.server.core.handler.ciba.CibaAuthorizeHandler;
import org.idp.server.core.handler.ciba.CibaDenyHandler;
import org.idp.server.core.handler.ciba.CibaRequestErrorHandler;
import org.idp.server.core.handler.ciba.CibaRequestHandler;
import org.idp.server.core.handler.ciba.io.*;
import org.idp.server.core.protcol.CibaApi;

public class CibaApiImpl implements CibaApi {

  CibaRequestHandler cibaRequestHandler;
  CibaAuthorizeHandler cibaAuthorizeHandler;
  CibaDenyHandler cibaDenyHandler;
  CibaRequestErrorHandler errorHandler;
  Logger log = Logger.getLogger(CibaApiImpl.class.getName());

  public CibaApiImpl(
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
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new CibaAuthorizeResponse(CibaAuthorizeStatus.SERVER_ERROR);
    }
  }

  public CibaDenyResponse deny(CibaDenyRequest request) {
    try {
      return cibaDenyHandler.handle(request);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new CibaDenyResponse(CibaDenyStatus.SERVER_ERROR);
    }
  }
}
