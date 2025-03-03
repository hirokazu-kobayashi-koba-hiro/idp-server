package org.idp.server.application.service.tenant;

import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.idp.server.domain.model.tenant.TenantRepository;
import org.springframework.stereotype.Service;

@Service
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
