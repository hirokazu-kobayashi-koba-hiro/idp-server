package org.idp.server.core.organization.initial;

import java.util.UUID;
import org.idp.server.core.tenant.*;

public class TenantCreator {
  TenantType tenantType;
  ServerDomain serverDomain;

  public TenantCreator(TenantType tenantType, ServerDomain serverDomain) {
    this.tenantType = tenantType;
    this.serverDomain = serverDomain;
  }

  public Tenant create() {
    String tenantId = UUID.randomUUID().toString();
    TenantIdentifier tenantIdentifier = new TenantIdentifier(tenantId);
    TenantName tenantName = new TenantName(tenantId);
    // TODO add logic
    TenantDomain tenantDomain = new TenantDomain(serverDomain.value() + tenantId);
    return new Tenant(tenantIdentifier, tenantName, tenantType, tenantDomain);
  }
}
