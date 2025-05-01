package org.idp.server.core.token.handler.tokenintrospection.io;

import java.util.Map;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.token.tokenintrospection.TokenIntrospectionRequestParameters;

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
