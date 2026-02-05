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

package org.idp.server.core.openid.identity.device.credential;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonReadable;

/**
 * Device credential for authentication.
 *
 * <p>Generic credential container that holds type-specific data as a Map. Consumers should use
 * type-specific data classes to extract relevant fields:
 *
 * <ul>
 *   <li>{@link JwtBearerCredentialData} for JWT Bearer credentials
 *   <li>{@link FidoCredentialData} for FIDO2/UAF credentials
 * </ul>
 */
public class DeviceCredential implements Serializable, JsonReadable {
  String id;
  String type;
  Map<String, Object> typeSpecificData;
  LocalDateTime createdAt;
  LocalDateTime expiresAt;
  LocalDateTime revokedAt;

  public DeviceCredential() {}

  public DeviceCredential(
      String id,
      String type,
      Map<String, Object> typeSpecificData,
      LocalDateTime createdAt,
      LocalDateTime expiresAt,
      LocalDateTime revokedAt) {
    this.id = id;
    this.type = type;
    this.typeSpecificData = typeSpecificData != null ? typeSpecificData : new HashMap<>();
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
  }

  public DeviceCredential(
      DeviceCredentialIdentifier identifier,
      DeviceCredentialType credentialType,
      Map<String, Object> typeSpecificData,
      LocalDateTime createdAt,
      LocalDateTime expiresAt,
      LocalDateTime revokedAt) {
    this.id = identifier.value();
    this.type = credentialType.name();
    this.typeSpecificData = typeSpecificData != null ? typeSpecificData : new HashMap<>();
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
  }

  public DeviceCredentialIdentifier identifier() {
    return new DeviceCredentialIdentifier(id);
  }

  public String id() {
    return id;
  }

  public DeviceCredentialType type() {
    if (type == null || type.isEmpty()) {
      return DeviceCredentialType.jwt_bearer_symmetric;
    }
    return DeviceCredentialType.valueOf(type);
  }

  public boolean isSymmetric() {
    return type().isSymmetric();
  }

  public boolean isAsymmetric() {
    return type().isAsymmetric();
  }

  public boolean isJwtBearer() {
    return type().isJwtBearer();
  }

  public boolean isFido() {
    return type().isFido();
  }

  public boolean isFido2() {
    return type().isFido2();
  }

  public boolean isFidoUaf() {
    return type().isFidoUaf();
  }

  public Map<String, Object> typeSpecificData() {
    return typeSpecificData != null ? typeSpecificData : new HashMap<>();
  }

  /** Returns JWT Bearer specific data. Use only when isJwtBearer() is true. */
  public JwtBearerCredentialData jwtBearerData() {
    return JwtBearerCredentialData.from(typeSpecificData());
  }

  /** Returns FIDO specific data. Use only when isFido() is true. */
  public FidoCredentialData fidoData() {
    return FidoCredentialData.from(typeSpecificData());
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public boolean hasCreatedAt() {
    return createdAt != null;
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  public boolean hasExpiresAt() {
    return expiresAt != null;
  }

  public boolean isExpired() {
    if (!hasExpiresAt()) {
      return false;
    }
    return SystemDateTime.now().isAfter(expiresAt);
  }

  public boolean isActive() {
    return exists() && !isExpired() && !isRevoked();
  }

  public LocalDateTime revokedAt() {
    return revokedAt;
  }

  public boolean hasRevokedAt() {
    return revokedAt != null;
  }

  public boolean isRevoked() {
    return hasRevokedAt();
  }

  public LocalDateTime expiresAtOrNull() {
    return expiresAt;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  /** Converts typeSpecificData to JSON for database storage. */
  public String typeSpecificDataAsJson() {
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    return jsonConverter.write(typeSpecificData());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    DeviceCredential that = (DeviceCredential) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("credential_type", type);
    map.put("type_specific_data", typeSpecificData());
    if (hasCreatedAt()) map.put("created_at", createdAt.toString());
    if (hasExpiresAt()) map.put("expires_at", expiresAt.toString());
    if (hasRevokedAt()) map.put("revoked_at", revokedAt.toString());
    return map;
  }
}
