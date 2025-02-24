package org.idp.sample.domain.model.authorization;

import org.idp.server.basic.http.BasicAuth;
import org.idp.server.token.AuthorizationHeaderHandlerable;

public class AdminCredentialConvertor implements AuthorizationHeaderHandlerable {

  String authorizationHeader;

  public AdminCredentialConvertor(String authorizationHeader) {
    this.authorizationHeader = authorizationHeader;
  }

  public BasicAuth toBasicAuth() {
    return convertBasicAuth(authorizationHeader);
  }
}
