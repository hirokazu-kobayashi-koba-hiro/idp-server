package org.idp.server.core.handler.oauth.io;

import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.extension.OAuthDenyReason;

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
