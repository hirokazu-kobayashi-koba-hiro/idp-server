package org.idp.server.core.ciba.handler.io;

import org.idp.server.core.ciba.CibaRequestContext;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.ciba.user.UserHint;
import org.idp.server.core.ciba.user.UserHintRelatedParams;
import org.idp.server.core.ciba.user.UserHintType;
import org.idp.server.core.type.ContentType;

public class CibaRequestResult {
  CibaRequestStatus status;
  CibaRequestContext cibaRequestContext;
  BackchannelAuthenticationErrorResponse errorResponse;
  ContentType contentType;

  public CibaRequestResult(CibaRequestStatus status, CibaRequestContext cibaRequestContext) {
    this.status = status;
    this.cibaRequestContext = cibaRequestContext;
    this.errorResponse = new BackchannelAuthenticationErrorResponse();
    // FIXME consider
    this.contentType = ContentType.application_json;
  }

  public CibaRequestResult(
      CibaRequestStatus status, BackchannelAuthenticationErrorResponse errorResponse) {
    this.status = status;
    this.errorResponse = errorResponse;
    // FIXME consider
    this.contentType = ContentType.application_json;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public BackchannelAuthenticationRequest request() {
    return cibaRequestContext.backchannelAuthenticationRequest();
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
    return errorResponse.contents();
  }

  public boolean isOK() {
    return status.isOK();
  }

  public CibaRequestContext context() {
    return cibaRequestContext;
  }

  public CibaRequestResponse toErrorResponse() {
    return new CibaRequestResponse(status, errorResponse);
  }

  public UserHintType userHintType() {
    return cibaRequestContext.userHintType();
  }

  public UserHint userhint() {
    return cibaRequestContext.userHint();
  }

  public UserHintRelatedParams userHintRelatedParams() {
    return cibaRequestContext.userHintRelatedParams();
  }
}
