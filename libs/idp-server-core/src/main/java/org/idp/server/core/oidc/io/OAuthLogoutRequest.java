package org.idp.server.core.oidc.io;

import java.util.Map;
import org.idp.server.core.oidc.request.OAuthLogoutParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthLogoutRequest {
  Tenant tenant;
  Map<String, String[]> params;

  public OAuthLogoutRequest() {
    this.params = Map.of();
  }

  public OAuthLogoutRequest(Tenant tenant, Map<String, String[]> params) {
    this.tenant = tenant;
    this.params = params;
  }

  public Tenant tenant() {
    return tenant;
  }

  public OAuthLogoutParameters toParameters() {
    return new OAuthLogoutParameters(params);
  }
}
