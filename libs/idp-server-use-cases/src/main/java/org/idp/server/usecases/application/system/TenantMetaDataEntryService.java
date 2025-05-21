package org.idp.server.usecases.application.system;

import org.idp.server.platform.datasource.Transaction;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantMetaDataApi;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;

@Transaction(readOnly = true)
public class TenantMetaDataEntryService implements TenantMetaDataApi {

  TenantQueryRepository tenantQueryRepository;

  public TenantMetaDataEntryService(TenantQueryRepository tenantQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public Tenant get(TenantIdentifier tenantIdentifier) {
    return tenantQueryRepository.get(tenantIdentifier);
  }
}
