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
 * including maximum device limits, identity verification requirements, and device authentication
 * method.
 *
 * <p>Previously defined per AuthenticationPolicy, now centralized at tenant level for consistency.
 *
 * @see TenantIdentityPolicy
 * @see DeviceAuthenticationType
 */
public class AuthenticationDeviceRule {

  private static final int DEFAULT_MAX_DEVICES = 5;
  private static final boolean DEFAULT_REQUIRED_IDENTITY_VERIFICATION = false;
  private static final DeviceAuthenticationType DEFAULT_AUTHENTICATION_TYPE =
      DeviceAuthenticationType.none;
  private static final boolean DEFAULT_ISSUE_DEVICE_SECRET = false;
  private static final String DEFAULT_DEVICE_SECRET_ALGORITHM = "HS256";

  private int maxDevices;
  private boolean requiredIdentityVerification;
  private DeviceAuthenticationType authenticationType;
  private boolean issueDeviceSecret;
  private String deviceSecretAlgorithm;
  private Long deviceSecretExpiresInSeconds;

  public AuthenticationDeviceRule() {
    this.maxDevices = DEFAULT_MAX_DEVICES;
    this.requiredIdentityVerification = DEFAULT_REQUIRED_IDENTITY_VERIFICATION;
    this.authenticationType = DEFAULT_AUTHENTICATION_TYPE;
    this.issueDeviceSecret = DEFAULT_ISSUE_DEVICE_SECRET;
    this.deviceSecretAlgorithm = DEFAULT_DEVICE_SECRET_ALGORITHM;
    this.deviceSecretExpiresInSeconds = null;
  }

  public AuthenticationDeviceRule(int maxDevices, boolean requiredIdentityVerification) {
    this(maxDevices, requiredIdentityVerification, DEFAULT_AUTHENTICATION_TYPE);
  }

  public AuthenticationDeviceRule(
      int maxDevices,
      boolean requiredIdentityVerification,
      DeviceAuthenticationType authenticationType) {
    this(
        maxDevices,
        requiredIdentityVerification,
        authenticationType,
        DEFAULT_ISSUE_DEVICE_SECRET,
        DEFAULT_DEVICE_SECRET_ALGORITHM,
        null);
  }

  public AuthenticationDeviceRule(
      int maxDevices,
      boolean requiredIdentityVerification,
      DeviceAuthenticationType authenticationType,
      boolean issueDeviceSecret,
      String deviceSecretAlgorithm,
      Long deviceSecretExpiresInSeconds) {
    this.maxDevices = maxDevices;
    this.requiredIdentityVerification = requiredIdentityVerification;
    this.authenticationType = authenticationType;
    this.issueDeviceSecret = issueDeviceSecret;
    this.deviceSecretAlgorithm =
        deviceSecretAlgorithm != null ? deviceSecretAlgorithm : DEFAULT_DEVICE_SECRET_ALGORITHM;
    this.deviceSecretExpiresInSeconds = deviceSecretExpiresInSeconds;
  }

  /**
   * Creates default authentication device rule.
   *
   * <p>Default values:
   *
   * <ul>
   *   <li>max_devices: 5
   *   <li>required_identity_verification: false
   *   <li>authentication_type: none
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

    DeviceAuthenticationType authenticationType = DEFAULT_AUTHENTICATION_TYPE;
    if (map.containsKey("authentication_type")) {
      Object value = map.get("authentication_type");
      if (value instanceof String) {
        try {
          authenticationType = DeviceAuthenticationType.valueOf((String) value);
        } catch (IllegalArgumentException e) {
          // Keep default if invalid value
        }
      }
    }

    boolean issueDeviceSecret = DEFAULT_ISSUE_DEVICE_SECRET;
    if (map.containsKey("issue_device_secret")) {
      Object value = map.get("issue_device_secret");
      if (value instanceof Boolean) {
        issueDeviceSecret = (Boolean) value;
      }
    }

    String deviceSecretAlgorithm = DEFAULT_DEVICE_SECRET_ALGORITHM;
    if (map.containsKey("device_secret_algorithm")) {
      Object value = map.get("device_secret_algorithm");
      if (value instanceof String) {
        deviceSecretAlgorithm = (String) value;
      }
    }

    Long deviceSecretExpiresInSeconds = null;
    if (map.containsKey("device_secret_expires_in_seconds")) {
      Object value = map.get("device_secret_expires_in_seconds");
      if (value instanceof Number) {
        deviceSecretExpiresInSeconds = ((Number) value).longValue();
      }
    }

    return new AuthenticationDeviceRule(
        maxDevices,
        requiredIdentityVerification,
        authenticationType,
        issueDeviceSecret,
        deviceSecretAlgorithm,
        deviceSecretExpiresInSeconds);
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
   * Returns the authentication type required for device assertions.
   *
   * <p>This determines how devices must authenticate when making requests (e.g., JWT Bearer Grant
   * with device-signed JWT).
   *
   * @return device authentication type
   */
  public DeviceAuthenticationType authenticationType() {
    return authenticationType;
  }

  /**
   * Returns whether device authentication is required (not none).
   *
   * @return true if authentication is required
   */
  public boolean requiresDeviceAuthentication() {
    return authenticationType.requiresCredential();
  }

  /**
   * Returns whether device secret should be issued on device registration.
   *
   * <p>When enabled, idp-server generates a symmetric key (HMAC secret) during device registration
   * and returns it in the response. This secret can be used for JWT Bearer Grant authentication.
   *
   * @return true if device secret should be issued
   */
  public boolean issueDeviceSecret() {
    return issueDeviceSecret;
  }

  /**
   * Returns the algorithm for device secret JWT signing.
   *
   * <p>Supported values: HS256, HS384, HS512
   *
   * @return device secret algorithm (default: HS256)
   */
  public String deviceSecretAlgorithm() {
    return deviceSecretAlgorithm;
  }

  /**
   * Returns the expiration time in seconds for device secrets.
   *
   * @return expiration time in seconds, or null for no expiration
   */
  public Long deviceSecretExpiresInSeconds() {
    return deviceSecretExpiresInSeconds;
  }

  /**
   * Returns whether device secret has expiration.
   *
   * @return true if device secret expires
   */
  public boolean hasDeviceSecretExpiration() {
    return deviceSecretExpiresInSeconds != null && deviceSecretExpiresInSeconds > 0;
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
    map.put("authentication_type", authenticationType.name());
    map.put("issue_device_secret", issueDeviceSecret);
    map.put("device_secret_algorithm", deviceSecretAlgorithm);
    if (deviceSecretExpiresInSeconds != null) {
      map.put("device_secret_expires_in_seconds", deviceSecretExpiresInSeconds);
    }
    return map;
  }
}
