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
package org.idp.server.core.adapters.datasource.crypto.datakey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.crypto.datakey.TenantDataKey;
import org.idp.server.platform.crypto.datakey.TenantDataKeyOperationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Data keys across tenants. Runs under the administrator's tenant, whose connection is not limited
 * to one tenant's rows, like the expired-data deletion.
 */
public class TenantDataKeyOperationDataSource implements TenantDataKeyOperationRepository {

  TenantDataKeySqlExecutor executor;

  public TenantDataKeyOperationDataSource(TenantDataKeySqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public List<TenantDataKey> findWrappedByOtherThan(Tenant tenant, String kekId, int limit) {
    return executor.selectWrappedByOtherThan(kekId, limit).stream()
        .map(ModelConverter::convert)
        .toList();
  }

  @Override
  public void updateWrapping(Tenant tenant, TenantDataKey key) {
    executor.updateWrapping(key);
  }

  @Override
  public Map<String, Long> countByKekId(Tenant tenant) {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (Map<String, String> row : executor.countByKekId()) {
      counts.put(row.get("kek_id"), Long.parseLong(row.get("data_keys")));
    }
    return counts;
  }
}
