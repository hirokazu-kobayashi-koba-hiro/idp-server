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

package org.idp.server.control_plane.management.oidc.clientinstance.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

/**
 * Request for registering a Client Instance.
 *
 * <p>Carries the Client Instance Key (public JWK) to trust for {@code attest_jwt_client_auth} with
 * {@code client_attestation_trust_source = registered_instance_key}.
 */
public class ClientInstanceRegistrationRequest implements ClientInstanceManagementRequest {

  RequestedClientId requestedClientId;
  Map<String, Object> values;

  public ClientInstanceRegistrationRequest(
      RequestedClientId requestedClientId, Map<String, Object> values) {
    this.requestedClientId = requestedClientId;
    this.values = values != null ? values : new HashMap<>();
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }

  public String id() {
    return optString("id");
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> instanceKey() {
    Object value = values.get("instance_key");
    return value instanceof Map ? (Map<String, Object>) value : Map.of();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> attestationEvidence() {
    Object value = values.get("attestation_evidence");
    return value instanceof Map ? (Map<String, Object>) value : Map.of();
  }

  public String deviceId() {
    return optString("device_id");
  }

  public String expiresAt() {
    return optString("expires_at");
  }

  private String optString(String key) {
    Object value = values.get(key);
    return value instanceof String ? (String) value : null;
  }

  @Override
  public Map<String, Object> toMap() {
    // instance_key is a public key, safe to record in the audit log
    Map<String, Object> map = new HashMap<>(values);
    map.put("client_id", requestedClientId.value());
    return map;
  }
}
