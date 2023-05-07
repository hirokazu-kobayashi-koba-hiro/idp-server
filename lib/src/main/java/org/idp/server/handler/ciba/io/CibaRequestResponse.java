package org.idp.server.handler.ciba.io;

import org.idp.server.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.handler.io.ContentType;

public class CibaRequestResponse {
  CibaRequestStatus status;
  BackchannelAuthenticationResponse response;
  BackchannelAuthenticationErrorResponse errorResponse;
  ContentType contentType;

  public CibaRequestResponse(CibaRequestStatus status, BackchannelAuthenticationResponse response) {
    this.status = status;
    this.response = response;
    this.errorResponse = new BackchannelAuthenticationErrorResponse();
    this.contentType = ContentType.application_json;
  }

  public CibaRequestResponse(
      CibaRequestStatus status, BackchannelAuthenticationErrorResponse errorResponse) {
    this.status = status;
    this.response = new BackchannelAuthenticationResponse();
    this.errorResponse = errorResponse;
    this.contentType = ContentType.application_json;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public BackchannelAuthenticationResponse response() {
    return response;
  }

  public BackchannelAuthenticationErrorResponse errorResponse() {
    return errorResponse;
  }

  public ContentType contentType() {
    return contentType;
  }

  public String contentTypeValue() {
    return contentType.value();
  }

  public String contents() {
    if (status.isOK()) {
      return response.contents();
    }
    return errorResponse.contents();
  }
}
