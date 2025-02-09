package org.idp.sample.application.service;

import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.domain.model.tenant.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantService {
  TenantRepository tenantRepository;

  public TenantService(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  public Tenant get(TenantIdentifier tenantIdentifier) {
    return tenantRepository.get(tenantIdentifier);
  }
}
