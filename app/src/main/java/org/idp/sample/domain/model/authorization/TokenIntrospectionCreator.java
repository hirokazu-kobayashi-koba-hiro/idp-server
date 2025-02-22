package org.idp.sample.domain.model.authorization;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.token.AuthorizationHeaderHandlerable;

public class TokenIntrospectionCreator implements AuthorizationHeaderHandlerable {

  String authorizationHeader;
  String issuer;

  public TokenIntrospectionCreator(String authorizationHeader, String issuer) {
    this.authorizationHeader = authorizationHeader;
    this.issuer = issuer;
  }

  public TokenIntrospectionRequest create() {
    var accessToken = extractAccessToken(authorizationHeader);
    Map<String, String[]> map = new HashMap<>();
    map.put("token", new String[] {accessToken.value()});
    return new TokenIntrospectionRequest(map, issuer);
  }
}
