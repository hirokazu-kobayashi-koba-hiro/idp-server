package org.idp.server.control_plane;

import org.idp.server.basic.http.BasicAuth;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;

public class AdminCredentialConvertor implements AuthorizationHeaderHandlerable {

  String authorizationHeader;

  public AdminCredentialConvertor(String authorizationHeader) {
    this.authorizationHeader = authorizationHeader;
  }

  public BasicAuth toBasicAuth() {
    return convertBasicAuth(authorizationHeader);
  }
}
