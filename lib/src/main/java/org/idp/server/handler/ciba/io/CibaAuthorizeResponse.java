package org.idp.server.handler.ciba.io;

public class CibaAuthorizeResponse {
  CibaAuthorizeStatus status;

  public CibaAuthorizeResponse(CibaAuthorizeStatus status) {
    this.status = status;
  }

  public int statusCode() {
    return status.statusCode();
  }
}
