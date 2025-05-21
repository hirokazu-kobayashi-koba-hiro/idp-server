package org.idp.server.core.oidc.io;

import org.idp.server.basic.type.extension.OAuthDenyReason;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthDenyRequest {
  Tenant tenant;
  String id;
  OAuthDenyReason denyReason;

  public OAuthDenyRequest(Tenant tenant, String id, OAuthDenyReason denyReason) {
    this.tenant = tenant;
    this.id = id;
    this.denyReason = denyReason;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }

  public OAuthDenyReason denyReason() {
    return denyReason;
  }
}
