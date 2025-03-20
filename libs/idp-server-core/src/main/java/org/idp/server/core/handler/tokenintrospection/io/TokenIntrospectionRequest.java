package org.idp.server.core.handler.tokenintrospection.io;

import java.util.Map;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tokenintrospection.TokenIntrospectionRequestParameters;

public class TokenIntrospectionRequest {
  Tenant tenant;
  Map<String, String[]> params;

  public TokenIntrospectionRequest(Tenant tenant, Map<String, String[]> params) {
    this.tenant = tenant;
    this.params = params;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public TokenIntrospectionRequestParameters toParameters() {
    return new TokenIntrospectionRequestParameters(params);
  }

  public Tenant tenant() {
    return tenant;
  }

  public String token() {
    if (hasToken()) {
      return params.get("token")[0];
    }
    return "";
  }

  public boolean hasToken() {
    return params.containsKey("token");
  }
}
