package org.idp.server.adapters.springboot.domain.model.authorization;

import org.idp.server.core.basic.http.BasicAuth;
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
