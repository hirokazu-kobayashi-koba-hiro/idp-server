package org.idp.server.core.organization.initial;

import java.util.UUID;
import org.idp.server.core.tenant.*;

public class TenantCreator {
  PublicTenantDomain publicTenantDomain;
  TenantName tenantName;

  public TenantCreator(PublicTenantDomain publicTenantDomain, TenantName tenantName) {
    this.publicTenantDomain = publicTenantDomain;
    this.tenantName = tenantName;
  }

  public Tenant create() {
    String id = UUID.randomUUID().toString();
    TenantIdentifier tenantIdentifier = new TenantIdentifier(id);
    String issuer = publicTenantDomain.value() + id;
    return new Tenant(tenantIdentifier, tenantName, TenantType.PUBLIC, issuer);
  }
}
