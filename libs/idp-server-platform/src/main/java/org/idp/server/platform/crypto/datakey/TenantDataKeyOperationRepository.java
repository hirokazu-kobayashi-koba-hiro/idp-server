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
package org.idp.server.platform.crypto.datakey;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Data keys across tenants, for the system administrator's key operations.
 *
 * <p>Rotating a key encryption key re-wraps every tenant's data keys, which is one operation over
 * all tenants, like the expired-data deletion.
 */
public interface TenantDataKeyOperationRepository {

  /**
   * @param tenant the administrator's tenant the operation runs under
   * @param kekId the key encryption key the returned keys are not wrapped by
   * @param limit the most keys returned
   */
  List<TenantDataKey> findWrappedByOtherThan(Tenant tenant, String kekId, int limit);

  /** Stores a new wrapping of the same data key. */
  void updateWrapping(Tenant tenant, TenantDataKey key);

  /** Data keys of any tenant matching the filters, by tenant, purpose and newest first. */
  List<TenantDataKey> findList(Tenant tenant, TenantDataKeyQueries queries);

  /** How many data keys match the filters, regardless of limit and offset. */
  long findTotalCount(Tenant tenant, TenantDataKeyQueries queries);

  /** How many data keys, destroyed ones aside, each key encryption key wraps. */
  Map<String, Long> countByKekId(Tenant tenant);
}
