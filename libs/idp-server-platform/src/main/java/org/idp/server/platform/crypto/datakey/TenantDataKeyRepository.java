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

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** A tenant's data keys. */
public interface TenantDataKeyRepository {

  /** The active key for the purpose, or an empty one when the tenant has none yet. */
  TenantDataKey findActive(Tenant tenant, String purpose);

  /**
   * The active key as last committed, for reading back after losing a concurrent creation.
   *
   * <p>A plain read can miss it: on MySQL the transaction's snapshot was taken before the winner
   * committed. This read sees the committed row.
   */
  TenantDataKey findActiveCommitted(Tenant tenant, String purpose);

  /** The key with this id, or an empty one. */
  TenantDataKey find(Tenant tenant, String id);

  /** The highest version the tenant has for the purpose; 0 when it has none. */
  int latestVersion(Tenant tenant, String purpose);

  /**
   * Stores the key unless another active key or the same version already exists.
   *
   * @return whether this key was stored. False means a concurrent request created one first.
   */
  boolean registerIfAbsent(Tenant tenant, TenantDataKey key);

  void updateStatus(Tenant tenant, String id, DataKeyStatus status);
}
