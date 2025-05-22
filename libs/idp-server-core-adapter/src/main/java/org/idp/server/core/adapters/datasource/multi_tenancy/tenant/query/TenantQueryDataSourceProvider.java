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


package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

public class TenantQueryDataSourceProvider
    implements ApplicationComponentProvider<TenantQueryRepository> {

  @Override
  public Class<TenantQueryRepository> type() {
    return TenantQueryRepository.class;
  }

  @Override
  public TenantQueryRepository provide(ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);
    return new TenantQueryDataSource(cacheStore);
  }
}
