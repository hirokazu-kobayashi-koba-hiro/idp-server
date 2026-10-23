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
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface TenantDataKeySqlExecutor {

  Map<String, String> selectActive(Tenant tenant, String purpose);

  Map<String, String> selectOne(Tenant tenant, String id);

  Map<String, String> selectLatestVersion(Tenant tenant, String purpose);

  /**
   * @return false when another active key or the same version is already stored
   */
  boolean insertIfAbsent(Tenant tenant, TenantDataKey key);

  void updateStatus(Tenant tenant, String id, DataKeyStatus status);

  /** Across tenants: keys not wrapped by this KEK, destroyed ones aside. */
  List<Map<String, String>> selectWrappedByOtherThan(String kekId, int limit);

  /** Across tenants. */
  void updateWrapping(TenantDataKey key);

  /** Across tenants: data keys per KEK, destroyed ones aside. */
  List<Map<String, String>> countByKekId();
}
