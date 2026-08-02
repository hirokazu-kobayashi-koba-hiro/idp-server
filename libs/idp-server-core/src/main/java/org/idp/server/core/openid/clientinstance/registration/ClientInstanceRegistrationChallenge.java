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

package org.idp.server.core.openid.clientinstance.registration;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonReadable;

/**
 * Authorization ticket for registering a Client Instance.
 *
 * <p>The registration endpoint is unauthenticated — platform attestation evidence takes the role of
 * the credential — so the server decides at challenge issuance what may be registered and keeps
 * that decision here. The registration request carries only the challenge value; the client_id,
 * device_id and instance identifier are recovered from this ticket rather than read from the
 * request body.
 *
 * <p>The challenge value is what the client embeds in the platform evidence (the Android Key
 * Attestation challenge, and the {@code request_hash} / {@code client_data_hash} computed over
 * {@code challenge || canonical JWK}), so verifying the evidence against this ticket binds the
 * evidence to the client, the device and the instance key being registered.
 */
public class ClientInstanceRegistrationChallenge implements Serializable, JsonReadable {

  String challenge;
  String tenantId;
  String clientId;
  String deviceId;
  String instanceId;
  LocalDateTime expiresAt;
  LocalDateTime usedAt;
  LocalDateTime createdAt;

  public ClientInstanceRegistrationChallenge() {}

  public ClientInstanceRegistrationChallenge(
      String challenge,
      String tenantId,
      String clientId,
      String deviceId,
      String instanceId,
      LocalDateTime expiresAt,
      LocalDateTime usedAt,
      LocalDateTime createdAt) {
    this.challenge = challenge;
    this.tenantId = tenantId;
    this.clientId = clientId;
    this.deviceId = deviceId;
    this.instanceId = instanceId;
    this.expiresAt = expiresAt;
    this.usedAt = usedAt;
    this.createdAt = createdAt;
  }

  public String challenge() {
    return challenge;
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

  public String deviceId() {
    return deviceId;
  }

  public boolean hasDeviceId() {
    return deviceId != null && !deviceId.isEmpty();
  }

  /** Instance identifier assigned at issuance; becomes the {@code kid} of the self-signed CAJ. */
  public ClientInstanceIdentifier instanceIdentifier() {
    return new ClientInstanceIdentifier(instanceId);
  }

  public String instanceId() {
    return instanceId;
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  public boolean isExpired() {
    return expiresAt == null || SystemDateTime.now().isAfter(expiresAt);
  }

  public LocalDateTime usedAt() {
    return usedAt;
  }

  /** A challenge is single use: a second presentation is a replay. */
  public boolean isUsed() {
    return usedAt != null;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public boolean exists() {
    return challenge != null && !challenge.isEmpty();
  }

  /** Returns true when this ticket may still be consumed. */
  public boolean isConsumable() {
    return exists() && !isUsed() && !isExpired();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("challenge", challenge);
    map.put("client_id", clientId);
    map.put("instance_id", instanceId);
    if (hasDeviceId()) map.put("device_id", deviceId);
    if (expiresAt != null) map.put("expires_at", expiresAt.toString());
    if (usedAt != null) map.put("used_at", usedAt.toString());
    if (createdAt != null) map.put("created_at", createdAt.toString());
    return map;
  }
}
