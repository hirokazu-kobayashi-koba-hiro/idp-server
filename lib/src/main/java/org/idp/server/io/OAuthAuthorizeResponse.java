package org.idp.server.io;

import org.idp.server.core.oauth.response.AuthorizationErrorResponse;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.io.status.OAuthAuthorizeStatus;

/** OAuthAuthorizeResponse */
public class OAuthAuthorizeResponse {
  OAuthAuthorizeStatus status;
  AuthorizationResponse authorizationResponse;

  AuthorizationErrorResponse errorResponse;

  public OAuthAuthorizeResponse() {}

  public OAuthAuthorizeResponse(
      OAuthAuthorizeStatus status, AuthorizationResponse authorizationResponse) {
    this.status = status;
    this.authorizationResponse = authorizationResponse;
  }

  public OAuthAuthorizeStatus status() {
    return status;
  }

  public AuthorizationResponse authorizationResponse() {
    return authorizationResponse;
  }

  public String redirectUriValue() {
    return status.isOK()
        ? authorizationResponse.redirectUriValue()
        : errorResponse.redirectUriValue();
  }
}
