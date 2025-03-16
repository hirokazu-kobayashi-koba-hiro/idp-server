package org.idp.server.core.tenant;

import org.idp.server.core.type.oauth.TokenIssuer;

public class TenantService {
  TenantRepository tenantRepository;

  public TenantService(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  public Tenant get(TenantIdentifier tenantIdentifier) {
    return tenantRepository.get(tenantIdentifier);
  }

  public void register(Tenant tenant) {
    tenantRepository.register(tenant);
  }

  public Tenant find(TokenIssuer tokenIssuer) {
    return tenantRepository.find(tokenIssuer);
  }

  public Tenant getAdmin() {
    return tenantRepository.getAdmin();
  }
}
