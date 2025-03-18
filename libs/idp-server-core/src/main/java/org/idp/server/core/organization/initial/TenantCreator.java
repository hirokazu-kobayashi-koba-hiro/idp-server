package org.idp.server.core.organization.initial;

import org.idp.server.core.configuration.ServerIdentifier;
import org.idp.server.core.tenant.*;
import org.idp.server.core.type.oauth.TokenIssuer;

public class TenantCreator {

  TenantIdentifier tenantIdentifier;
  TenantName tenantName;
  ServerIdentifier serverIdentifier;
  TokenIssuer tokenIssuer;

  public TenantCreator(
      TenantIdentifier tenantIdentifier,
      TenantName tenantName,
      ServerIdentifier serverIdentifier,
      TokenIssuer tokenIssuer) {
    this.tenantIdentifier = tenantIdentifier;
    this.tenantName = tenantName;
    this.serverIdentifier = serverIdentifier;
    this.tokenIssuer = tokenIssuer;
  }

  public Tenant create() {
    return new Tenant(
        tenantIdentifier,
        tenantName,
        TenantType.PUBLIC,
        new TenantServerAttribute(serverIdentifier, tokenIssuer));
  }
}
