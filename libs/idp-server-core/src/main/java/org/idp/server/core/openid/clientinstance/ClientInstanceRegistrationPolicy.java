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

import java.util.Objects;

/**
 * How much a client instance registration must be backed by an authentication device.
 *
 * <ul>
 *   <li>{@link #require_authentication_device} — the device_id must be an authentication device
 *       registered at this Authorization Server. Registering such a device involves the user (FIDO
 *       UAF registration is performed with biometrics), so the unauthenticated registration
 *       endpoint gains an indirect "the user approved this device" backing
 *   <li>{@link #attestation_only} — no device is involved and the platform attestation is the only
 *       backing. Wallet style clients (OID4VCI / HAIP) have no authentication device
 *   <li>{@link #undefined} — not configured or unknown; registration is rejected rather than
 *       falling back to the weaker policy
 * </ul>
 */
public enum ClientInstanceRegistrationPolicy {
  require_authentication_device,
  attestation_only,
  undefined;

  public static ClientInstanceRegistrationPolicy of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (ClientInstanceRegistrationPolicy policy : ClientInstanceRegistrationPolicy.values()) {
      if (policy.name().equals(value)) {
        return policy;
      }
    }
    return undefined;
  }

  public boolean requiresAuthenticationDevice() {
    return this == require_authentication_device;
  }

  public boolean isUndefined() {
    return this == undefined;
  }
}
