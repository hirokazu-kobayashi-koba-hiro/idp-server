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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * One generation of a tenant's data key for one purpose, as stored: wrapped by a key encryption
 * key.
 */
public class TenantDataKey {

  String id;
  TenantIdentifier tenantIdentifier;
  String purpose;
  int version;
  DataKeyStatus status;
  EncryptedData wrappedKey;
  String kekId;
  LocalDateTime createdAt;

  public TenantDataKey() {}

  public TenantDataKey(
      String id,
      TenantIdentifier tenantIdentifier,
      String purpose,
      int version,
      DataKeyStatus status,
      EncryptedData wrappedKey,
      String kekId,
      LocalDateTime createdAt) {
    this.id = id;
    this.tenantIdentifier = tenantIdentifier;
    this.purpose = purpose;
    this.version = version;
    this.status = status;
    this.wrappedKey = wrappedKey;
    this.kekId = kekId;
    this.createdAt = createdAt;
  }

  public String id() {
    return id;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public String purpose() {
    return purpose;
  }

  public int version() {
    return version;
  }

  public DataKeyStatus status() {
    return status;
  }

  public EncryptedData wrappedKey() {
    return wrappedKey;
  }

  public String kekId() {
    return kekId;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  /** What an administrator may see of the key. The wrapped key itself is left out. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("tenant_id", tenantIdentifier.value());
    map.put("purpose", purpose);
    map.put("version", version);
    map.put("status", status.value());
    map.put("kek_id", kekId);
    map.put("created_at", createdAt == null ? null : createdAt.toString());
    return map;
  }

  public TenantDataKey rewrapped(EncryptedData wrappedKey, String kekId) {
    return new TenantDataKey(
        id, tenantIdentifier, purpose, version, status, wrappedKey, kekId, createdAt);
  }
}
