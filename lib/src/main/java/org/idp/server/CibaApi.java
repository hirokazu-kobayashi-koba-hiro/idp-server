package org.idp.server;

import org.idp.server.handler.ciba.CibaRequestHandler;

public class CibaApi {

  CibaRequestHandler cibaRequestHandler;

  CibaApi(CibaRequestHandler cibaRequestHandler) {
    this.cibaRequestHandler = cibaRequestHandler;
  }

  public void request() {}
}
