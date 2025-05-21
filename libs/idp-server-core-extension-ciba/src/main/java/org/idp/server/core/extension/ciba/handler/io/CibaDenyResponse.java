package org.idp.server.core.extension.ciba.handler.io;

public class CibaDenyResponse {
  CibaDenyStatus status;

  public CibaDenyResponse(CibaDenyStatus status) {
    this.status = status;
  }

  public int statusCode() {
    return status.statusCode();
  }
}
