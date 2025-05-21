package org.idp.server.core.oidc.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** OAuthRequest */
public class OAuthRequest {

  Tenant tenant;
  Map<String, String[]> params;
  String sessionId;

  public OAuthRequest(Tenant tenant, Map<String, String[]> params) {
    this.tenant = tenant;
    this.params = params;
  }

  public static OAuthRequest singleMap(Tenant tenant, Map<String, String> params) {
    HashMap<String, String[]> map = new HashMap<>();
    params.forEach(
        (key, value) -> {
          map.put(key, new String[] {value});
        });
    return new OAuthRequest(tenant, map);
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public Tenant tenant() {
    return tenant;
  }

  public OAuthRequestParameters toParameters() {
    return new OAuthRequestParameters(params);
  }
}
