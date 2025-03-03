package org.idp.server.core.handler.ciba.io;

public class CibaDenyResponse {
  CibaDenyStatus status;

  public CibaDenyResponse(CibaDenyStatus status) {
    this.status = status;
  }

  public int statusCode() {
    return status.statusCode();
  }
}
