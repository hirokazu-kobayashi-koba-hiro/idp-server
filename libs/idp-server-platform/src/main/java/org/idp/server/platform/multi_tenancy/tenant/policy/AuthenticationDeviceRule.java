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

package org.idp.server.platform.multi_tenancy.tenant.policy;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication device rule configuration for tenant-level device management.
 *
 * <p>This class defines the rules for managing authentication devices at the tenant level,
 * including maximum device limits and identity verification requirements.
 *
 * <p>Previously defined per AuthenticationPolicy, now centralized at tenant level for consistency.
 *
 * @see TenantIdentityPolicy
 */
public class AuthenticationDeviceRule {

  private static final int DEFAULT_MAX_DEVICES = 5;
  private static final boolean DEFAULT_REQUIRED_IDENTITY_VERIFICATION = false;

  private int maxDevices;
  private boolean requiredIdentityVerification;

  public AuthenticationDeviceRule() {
    this.maxDevices = DEFAULT_MAX_DEVICES;
    this.requiredIdentityVerification = DEFAULT_REQUIRED_IDENTITY_VERIFICATION;
  }

  public AuthenticationDeviceRule(int maxDevices, boolean requiredIdentityVerification) {
    this.maxDevices = maxDevices;
    this.requiredIdentityVerification = requiredIdentityVerification;
  }

  /**
   * Creates default authentication device rule.
   *
   * <p>Default values:
   *
   * <ul>
   *   <li>max_devices: 5
   *   <li>required_identity_verification: false
   * </ul>
   *
   * @return default authentication device rule
   */
  public static AuthenticationDeviceRule defaultRule() {
    return new AuthenticationDeviceRule();
  }

  /**
   * Creates authentication device rule from map.
   *
   * @param map configuration map
   * @return authentication device rule
   */
  public static AuthenticationDeviceRule fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return defaultRule();
    }

    int maxDevices = DEFAULT_MAX_DEVICES;
    if (map.containsKey("max_devices")) {
      Object value = map.get("max_devices");
      if (value instanceof Number) {
        maxDevices = ((Number) value).intValue();
      }
    }

    boolean requiredIdentityVerification = DEFAULT_REQUIRED_IDENTITY_VERIFICATION;
    if (map.containsKey("required_identity_verification")) {
      Object value = map.get("required_identity_verification");
      if (value instanceof Boolean) {
        requiredIdentityVerification = (Boolean) value;
      }
    }

    return new AuthenticationDeviceRule(maxDevices, requiredIdentityVerification);
  }

  /**
   * Returns maximum number of authentication devices allowed per user.
   *
   * @return max devices
   */
  public int maxDevices() {
    return maxDevices;
  }

  /**
   * Returns whether identity verification is required for device registration.
   *
   * @return true if identity verification is required
   */
  public boolean requiredIdentityVerification() {
    return requiredIdentityVerification;
  }

  /**
   * Converts this rule to a Map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("max_devices", maxDevices);
    map.put("required_identity_verification", requiredIdentityVerification);
    return map;
  }
}
