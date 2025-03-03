package org.idp.server.core.handler.userinfo.io;

import java.util.Map;
import org.idp.server.core.userinfo.UserinfoErrorResponse;
import org.idp.server.core.userinfo.UserinfoResponse;

public class UserinfoRequestResponse {
  UserinfoRequestStatus status;
  UserinfoResponse userinfoResponse;
  UserinfoErrorResponse errorResponse;

  public UserinfoRequestResponse(UserinfoRequestStatus status, UserinfoResponse userinfoResponse) {
    this.status = status;
    this.userinfoResponse = userinfoResponse;
    this.errorResponse = new UserinfoErrorResponse();
  }

  public UserinfoRequestResponse(
      UserinfoRequestStatus status, UserinfoErrorResponse errorResponse) {
    this.status = status;
    this.userinfoResponse = new UserinfoResponse();
    this.errorResponse = errorResponse;
  }

  public UserinfoRequestStatus status() {
    return status;
  }

  public Map<String, Object> response() {
    if (status.isOK()) {
      return userinfoResponse.response();
    }
    return errorResponse.response();
  }

  public UserinfoResponse userinfoResponse() {
    return userinfoResponse;
  }

  public UserinfoErrorResponse errorResponse() {
    return errorResponse;
  }

  public int statusCode() {
    return status.statusCode();
  }
}
