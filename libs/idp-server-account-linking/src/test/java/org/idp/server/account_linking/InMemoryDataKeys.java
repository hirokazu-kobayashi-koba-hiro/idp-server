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

package org.idp.server.account_linking;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.crypto.EncryptionKeyring;
import org.idp.server.platform.crypto.datakey.DataKeyService;
import org.idp.server.platform.crypto.datakey.DataKeyStatus;
import org.idp.server.platform.crypto.datakey.TenantDataKey;
import org.idp.server.platform.crypto.datakey.TenantDataKeyOperationRepository;
import org.idp.server.platform.crypto.datakey.TenantDataKeyQueries;
import org.idp.server.platform.crypto.datakey.TenantDataKeyRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/** Data keys held in memory, for tests that seal and open linked account tokens. */
class InMemoryDataKeys implements TenantDataKeyRepository, TenantDataKeyOperationRepository {

  static final Tenant TENANT =
      new Tenant() {
        @Override
        public TenantIdentifier identifier() {
          return new TenantIdentifier("11111111-1111-1111-1111-111111111111");
        }

        @Override
        public String identifierValue() {
          return "11111111-1111-1111-1111-111111111111";
        }
      };

  Map<String, TenantDataKey> rows = new LinkedHashMap<>();

  static DataKeyService service() {
    InMemoryDataKeys store = new InMemoryDataKeys();
    return new DataKeyService(
        store,
        store,
        EncryptionKeyring.of("", null, Base64.getEncoder().encodeToString(new byte[32])));
  }

  @Override
  public TenantDataKey findActive(Tenant tenant, String purpose) {
    return rows.values().stream()
        .filter(k -> k.purpose().equals(purpose) && k.status() == DataKeyStatus.ACTIVE)
        .findFirst()
        .orElse(new TenantDataKey());
  }

  @Override
  public TenantDataKey findActiveCommitted(Tenant tenant, String purpose) {
    return findActive(tenant, purpose);
  }

  @Override
  public List<TenantDataKey> findList(Tenant tenant, TenantDataKeyQueries queries) {
    return List.copyOf(rows.values());
  }

  @Override
  public long findTotalCount(Tenant tenant, TenantDataKeyQueries queries) {
    return rows.size();
  }

  @Override
  public List<TenantDataKey> findList(Tenant tenant) {
    return List.copyOf(rows.values());
  }

  @Override
  public TenantDataKey find(Tenant tenant, String id) {
    return rows.getOrDefault(id, new TenantDataKey());
  }

  @Override
  public int latestVersion(Tenant tenant, String purpose) {
    return rows.values().stream().mapToInt(TenantDataKey::version).max().orElse(0);
  }

  @Override
  public boolean registerIfAbsent(Tenant tenant, TenantDataKey key) {
    if (findActive(tenant, key.purpose()).exists()) {
      return false;
    }
    rows.put(key.id(), key);
    return true;
  }

  @Override
  public void updateStatus(Tenant tenant, String id, DataKeyStatus status) {}

  @Override
  public List<TenantDataKey> findWrappedByOtherThan(Tenant tenant, String kekId, int limit) {
    return List.of();
  }

  @Override
  public void updateWrapping(Tenant tenant, TenantDataKey key) {}

  @Override
  public Map<String, Long> countByKekId(Tenant tenant) {
    return new HashMap<>();
  }
}
