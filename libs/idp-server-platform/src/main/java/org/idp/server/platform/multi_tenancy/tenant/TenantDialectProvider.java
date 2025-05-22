/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.multi_tenancy.tenant;

import org.idp.server.platform.datasource.DatabaseType;

public class TenantDialectProvider implements DialectProvider {

  TenantQueryRepository tenantQueryRepository;

  public TenantDialectProvider(TenantQueryRepository tenantQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public DatabaseType provide(TenantIdentifier tenantIdentifier) {
    if (AdminTenantContext.isAdmin(tenantIdentifier)) {
      return DatabaseType.POSTGRESQL;
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    return tenant.databaseType();
  }
}
