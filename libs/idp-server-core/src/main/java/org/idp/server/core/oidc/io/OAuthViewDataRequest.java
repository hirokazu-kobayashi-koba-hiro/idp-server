package org.idp.server.core.oidc.io;

import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class OAuthViewDataRequest {
  Tenant tenant;
  String id;

  public OAuthViewDataRequest(Tenant tenant, String id) {
    this.tenant = tenant;
    this.id = id;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }
}
