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

import java.util.List;
import java.util.Map;
import org.idp.server.platform.crypto.datakey.DataKeyStatus;
import org.idp.server.platform.crypto.datakey.TenantDataKey;
import org.idp.server.platform.crypto.datakey.TenantDataKeyRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TenantDataKeyDataSource implements TenantDataKeyRepository {

  TenantDataKeySqlExecutor executor;

  public TenantDataKeyDataSource(TenantDataKeySqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public TenantDataKey findActive(Tenant tenant, String purpose) {
    return ModelConverter.convert(executor.selectActive(tenant, purpose));
  }

  @Override
  public TenantDataKey findActiveCommitted(Tenant tenant, String purpose) {
    return ModelConverter.convert(executor.selectActiveCommitted(tenant, purpose));
  }

  @Override
  public TenantDataKey find(Tenant tenant, String id) {
    return ModelConverter.convert(executor.selectOne(tenant, id));
  }

  @Override
  public List<TenantDataKey> findList(Tenant tenant) {
    return executor.selectList(tenant).stream().map(ModelConverter::convert).toList();
  }

  @Override
  public int latestVersion(Tenant tenant, String purpose) {
    Map<String, String> row = executor.selectLatestVersion(tenant, purpose);
    if (row == null || row.get("version") == null) {
      return 0;
    }
    return Integer.parseInt(row.get("version"));
  }

  @Override
  public boolean registerIfAbsent(Tenant tenant, TenantDataKey key) {
    return executor.insertIfAbsent(tenant, key);
  }

  @Override
  public void updateStatus(Tenant tenant, String id, DataKeyStatus status) {
    executor.updateStatus(tenant, id, status);
  }
}
