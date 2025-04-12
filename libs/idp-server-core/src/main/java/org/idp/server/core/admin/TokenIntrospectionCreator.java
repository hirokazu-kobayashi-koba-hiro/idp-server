package org.idp.server.core.admin;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;

public class TokenIntrospectionCreator implements AuthorizationHeaderHandlerable {

  Tenant tenant;
  String authorizationHeader;

  public TokenIntrospectionCreator(Tenant tenant, String authorizationHeader) {
    this.tenant = tenant;
    this.authorizationHeader = authorizationHeader;
  }

  public TokenIntrospectionRequest create() {
    var accessToken = extractAccessToken(authorizationHeader);
    Map<String, String[]> map = new HashMap<>();
    map.put("token", new String[] {accessToken.value()});
    return new TokenIntrospectionRequest(tenant, map);
  }
}
