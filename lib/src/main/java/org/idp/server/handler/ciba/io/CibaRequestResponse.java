package org.idp.server.handler.ciba.io;

import org.idp.server.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.ciba.response.BackchannelAuthenticationResponse;

public class CibaRequestResponse {
  CibaRequestStatus status;
  BackchannelAuthenticationResponse response;
  BackchannelAuthenticationErrorResponse errorResponse;

  public CibaRequestResponse(CibaRequestStatus status, BackchannelAuthenticationResponse response) {
    this.status = status;
    this.response = response;
    this.errorResponse = new BackchannelAuthenticationErrorResponse();
  }

  public CibaRequestResponse(
      CibaRequestStatus status, BackchannelAuthenticationErrorResponse errorResponse) {
    this.status = status;
    this.response = new BackchannelAuthenticationResponse();
    this.errorResponse = errorResponse;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public String response() {
    if (status.isOK()) {
      return response.contents();
    }
    return "";
  }
}
