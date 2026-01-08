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

public class DeviceCredential implements Serializable, JsonReadable {
  String id;
  String type;
  String secretValue;
  String jwks;
  String algorithm;
  LocalDateTime createdAt;
  LocalDateTime expiresAt;
  LocalDateTime revokedAt;

  public DeviceCredential() {}

  public DeviceCredential(
      String id,
      String type,
      String secretValue,
      String jwks,
      String algorithm,
      LocalDateTime createdAt,
      LocalDateTime expiresAt) {
    this.id = id;
    this.type = type;
    this.secretValue = secretValue;
    this.jwks = jwks;
    this.algorithm = algorithm;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.revokedAt = null;
  }

  public DeviceCredential(
      DeviceCredentialIdentifier identifier,
      DeviceCredentialType credentialType,
      String secretValue,
      String jwks,
      String algorithm,
      LocalDateTime createdAt,
      LocalDateTime expiresAt,
      LocalDateTime revokedAt) {
    this.id = identifier.value();
    this.type = credentialType.name();
    this.secretValue = secretValue;
    this.jwks = jwks;
    this.algorithm = algorithm;
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
      return DeviceCredentialType.symmetric;
    }
    return DeviceCredentialType.valueOf(type);
  }

  public boolean isSymmetric() {
    return type().isSymmetric();
  }

  public boolean isAsymmetric() {
    return type().isAsymmetric();
  }

  public String secretValue() {
    return secretValue;
  }

  public boolean hasSecretValue() {
    return secretValue != null && !secretValue.isEmpty();
  }

  public String jwks() {
    return jwks;
  }

  public boolean hasJwks() {
    return jwks != null && !jwks.isEmpty();
  }

  public String algorithm() {
    return algorithm;
  }

  public boolean hasAlgorithm() {
    return algorithm != null && !algorithm.isEmpty();
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

  public String jwksAsJson() {
    return jwks;
  }

  public String typeSpecificDataAsJson() {
    Map<String, Object> data = new HashMap<>();
    if (hasAlgorithm()) {
      data.put("algorithm", algorithm);
    }
    if (hasSecretValue()) {
      data.put("secret_value", secretValue);
    }
    if (hasJwks()) {
      data.put("jwks", jwks);
    }
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    return jsonConverter.write(data);
  }

  public LocalDateTime expiresAtOrNull() {
    return expiresAt;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
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
    map.put("type", type);
    if (hasSecretValue()) map.put("secret_value", secretValue);
    if (hasJwks()) map.put("jwks", jwks);
    if (hasAlgorithm()) map.put("algorithm", algorithm);
    if (hasCreatedAt()) map.put("created_at", createdAt.toString());
    if (hasExpiresAt()) map.put("expires_at", expiresAt.toString());
    if (hasRevokedAt()) map.put("revoked_at", revokedAt.toString());
    return map;
  }
}
