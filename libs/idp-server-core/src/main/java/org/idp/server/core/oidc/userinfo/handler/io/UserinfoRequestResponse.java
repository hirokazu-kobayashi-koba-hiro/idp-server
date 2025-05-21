package org.idp.server.core.oidc.userinfo.handler.io;

import java.util.Map;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.userinfo.UserinfoErrorResponse;
import org.idp.server.core.oidc.userinfo.UserinfoResponse;
import org.idp.server.core.oidc.token.OAuthToken;

public class UserinfoRequestResponse {
  UserinfoRequestStatus status;
  OAuthToken oAuthToken;
  UserinfoResponse userinfoResponse;
  UserinfoErrorResponse errorResponse;

  public UserinfoRequestResponse(
      UserinfoRequestStatus status, OAuthToken oAuthToken, UserinfoResponse userinfoResponse) {
    this.status = status;
    this.oAuthToken = oAuthToken;
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

  public OAuthToken oAuthToken() {
    return oAuthToken;
  }

  public User user() {
    return userinfoResponse.user();
  }

  public UserinfoErrorResponse errorResponse() {
    return errorResponse;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public boolean isOK() {
    return status.isOK();
  }
}
