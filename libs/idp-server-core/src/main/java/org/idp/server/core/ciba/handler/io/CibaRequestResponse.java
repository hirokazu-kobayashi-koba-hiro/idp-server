package org.idp.server.core.ciba.handler.io;

import org.idp.server.core.ciba.CibaRequestContext;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.ContentType;

public class CibaRequestResponse {
  CibaRequestStatus status;
  BackchannelAuthenticationRequest request;
  BackchannelAuthenticationResponse response;
  User user;
  BackchannelAuthenticationErrorResponse errorResponse;
  ContentType contentType;

  public CibaRequestResponse(
      CibaRequestStatus status,
      CibaRequestContext cibaRequestContext,
      BackchannelAuthenticationResponse response,
      User user) {
    this.status = status;
    this.request = cibaRequestContext.backchannelAuthenticationRequest();
    this.response = response;
    this.user = user;
    this.errorResponse = new BackchannelAuthenticationErrorResponse();
    // FIXME consider
    this.contentType = ContentType.application_json;
  }

  public CibaRequestResponse(
      CibaRequestStatus status, BackchannelAuthenticationErrorResponse errorResponse) {
    this.status = status;
    this.response = new BackchannelAuthenticationResponse();
    this.errorResponse = errorResponse;
    // FIXME consider
    this.contentType = ContentType.application_json;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public BackchannelAuthenticationRequest request() {
    return request;
  }

  public BackchannelAuthenticationResponse response() {
    return response;
  }

  public User user() {
    return user;
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

  public boolean isOK() {
    return status.isOK();
  }
}
