/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.usecases.application.system;

import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantMetaDataApi;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

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
