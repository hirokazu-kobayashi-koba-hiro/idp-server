/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.command;

import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class TenantCommandDataSource implements TenantCommandRepository {

  TenantCommandSqlExecutor executor;
  CacheStore cacheStore;

  public TenantCommandDataSource(TenantCommandSqlExecutor executor, CacheStore cacheStore) {
    this.executor = executor;
    this.cacheStore = cacheStore;
  }

  @Override
  public void register(Tenant tenant) {
    executor.insert(tenant);
  }

  /**
   * Writes first and invalidates the cache after, as the other configuration data sources do.
   *
   * <p>Invalidating first leaves a window in which a read of the tenant — within the same request,
   * such as the audit log publishing after the update — loads the row not yet updated and puts it
   * back in the cache, where every instance then serves it until the entry expires (Issue #1881).
   */
  @Override
  public void update(Tenant tenant) {
    executor.update(tenant);
    cacheStore.delete(key(tenant.identifier()));
  }

  @Override
  public void delete(TenantIdentifier tenantIdentifier) {
    executor.delete(tenantIdentifier);
    cacheStore.delete(key(tenantIdentifier));
  }

  private String key(TenantIdentifier tenantIdentifier) {
    return "tenantId:" + tenantIdentifier.value() + ":" + Tenant.class.getSimpleName();
  }
}
