package org.idp.server.usecases.application;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantMetaDataApi;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;

@Transaction
public class TenantMetaDataEntryService implements TenantMetaDataApi {

  TenantRepository tenantRepository;

  public TenantMetaDataEntryService(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  @Override
  public Tenant get(TenantIdentifier tenantIdentifier) {
    return tenantRepository.get(tenantIdentifier);
  }
}
