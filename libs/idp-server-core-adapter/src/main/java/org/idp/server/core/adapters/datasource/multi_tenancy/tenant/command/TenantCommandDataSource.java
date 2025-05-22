/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.command;

import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class TenantCommandDataSource implements TenantCommandRepository {

  TenantCommandSqlExecutors executors;
  CacheStore cacheStore;

  public TenantCommandDataSource(CacheStore cacheStore) {
    this.executors = new TenantCommandSqlExecutors();
    this.cacheStore = cacheStore;
  }

  @Override
  public void register(Tenant tenant) {
    TenantCommandSqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);
    executor.insert(tenant);
  }

  @Override
  public void update(Tenant tenant) {
    String key = key(tenant.identifier());
    cacheStore.delete(key);
  }

  @Override
  public void delete(TenantIdentifier tenantIdentifier) {
    String key = key(tenantIdentifier);
    cacheStore.delete(key);
  }

  private String key(TenantIdentifier tenantIdentifier) {
    return "tenantId:" + tenantIdentifier.value() + ":" + Tenant.class.getSimpleName();
  }
}
