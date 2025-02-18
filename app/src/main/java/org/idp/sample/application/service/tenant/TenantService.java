package org.idp.sample.application.service.tenant;

import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.domain.model.tenant.TenantRepository;
import org.idp.server.type.oauth.TokenIssuer;
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
