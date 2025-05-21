package org.idp.server.core.extension.ciba.handler.io;

import org.idp.server.basic.type.ContentType;
import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationResponse;

public class CibaRequestResponse {
  CibaRequestStatus status;
  BackchannelAuthenticationResponse backchannelAuthenticationResponse;
  BackchannelAuthenticationErrorResponse errorResponse;
  ContentType contentType;

  public CibaRequestResponse(
      CibaRequestStatus status,
      BackchannelAuthenticationResponse backchannelAuthenticationResponse) {
    this.status = status;
    this.backchannelAuthenticationResponse = backchannelAuthenticationResponse;
    this.errorResponse = new BackchannelAuthenticationErrorResponse();
    // FIXME consider
    this.contentType = ContentType.application_json;
  }

  public CibaRequestResponse(
      CibaRequestStatus status, BackchannelAuthenticationErrorResponse errorResponse) {
    this.status = status;
    this.errorResponse = errorResponse;
    // FIXME consider
    this.contentType = ContentType.application_json;
  }

  public int statusCode() {
    return status.statusCode();
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
      return backchannelAuthenticationResponse.contents();
    }
    return errorResponse.contents();
  }

  public boolean isOK() {
    return status.isOK();
  }
}
