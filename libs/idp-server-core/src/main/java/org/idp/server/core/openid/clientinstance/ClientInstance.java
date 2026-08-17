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

package org.idp.server.core.openid.clientinstance;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonReadable;

/**
 * A registered instance of a client — a single installation of an application on a single device.
 *
 * <p>Introduced for Attestation-Based Client Authentication: with {@code
 * client_attestation_trust_source = registered_instance_key} the Client Attestation JWT is
 * self-signed by the Client Instance Key (CIK), so the Authorization Server trusts the key it
 * registered itself rather than a Client Attester key.
 *
 * <p>The instance sits below the client and beside the user's authentication device: it is scoped
 * by {@code (tenant, client_id)} and has a lifecycle of its own, so a CIK can be revoked without
 * touching the user's FIDO registrations.
 */
public class ClientInstance implements Serializable, JsonReadable {

  String id;
  String tenantId;
  String clientId;
  Map<String, Object> instanceKey = new HashMap<>();
  String status;
  Map<String, Object> attestationEvidence = new HashMap<>();
  String deviceId;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
  LocalDateTime expiresAt;
  LocalDateTime revokedAt;

  public ClientInstance() {}

  public ClientInstance(
      String id,
      String tenantId,
      String clientId,
      Map<String, Object> instanceKey,
      String status,
      Map<String, Object> attestationEvidence,
      String deviceId,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      LocalDateTime expiresAt,
      LocalDateTime revokedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.clientId = clientId;
    this.instanceKey = instanceKey != null ? instanceKey : new HashMap<>();
    this.status = status;
    this.attestationEvidence = attestationEvidence != null ? attestationEvidence : new HashMap<>();
    this.deviceId = deviceId;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
  }

  public ClientInstanceIdentifier identifier() {
    return new ClientInstanceIdentifier(id);
  }

  public String id() {
    return id;
  }

  public String tenantId() {
    return tenantId;
  }

  public RequestedClientId requestedClientId() {
    return new RequestedClientId(clientId);
  }

  public String clientId() {
    return clientId;
  }

  /** Returns the Client Instance Key as a JWK map. Public key material only. */
  public Map<String, Object> instanceKey() {
    return instanceKey != null ? instanceKey : new HashMap<>();
  }

  /** Returns the Client Instance Key wrapped as a single-key JWKS document. */
  public String instanceKeyAsJwks() {
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    return jsonConverter.write(Map.of("keys", java.util.List.of(instanceKey())));
  }

  public ClientInstanceStatus status() {
    return ClientInstanceStatus.of(status);
  }

  public Map<String, Object> attestationEvidence() {
    return attestationEvidence != null ? attestationEvidence : new HashMap<>();
  }

  public boolean hasAttestationEvidence() {
    return attestationEvidence != null && !attestationEvidence.isEmpty();
  }

  public String deviceId() {
    return deviceId;
  }

  public boolean hasDeviceId() {
    return deviceId != null && !deviceId.isEmpty();
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime updatedAt() {
    return updatedAt;
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

  public LocalDateTime revokedAt() {
    return revokedAt;
  }

  public boolean isRevoked() {
    return revokedAt != null || status().isRevoked();
  }

  /** Returns true when this instance may be used for client authentication. */
  public boolean isActive() {
    return exists() && !isExpired() && !isRevoked();
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ClientInstance that = (ClientInstance) o;
    return Objects.equals(id, that.id)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(clientId, that.clientId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tenantId, clientId);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("client_id", clientId);
    map.put("instance_key", instanceKey());
    map.put("status", status);
    if (hasAttestationEvidence()) map.put("attestation_evidence", attestationEvidence);
    if (hasDeviceId()) map.put("device_id", deviceId);
    if (createdAt != null) map.put("created_at", createdAt.toString());
    if (updatedAt != null) map.put("updated_at", updatedAt.toString());
    if (expiresAt != null) map.put("expires_at", expiresAt.toString());
    if (revokedAt != null) map.put("revoked_at", revokedAt.toString());
    return map;
  }
}
