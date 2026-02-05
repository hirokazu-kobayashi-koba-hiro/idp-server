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

package org.idp.server.core.openid.identity.device;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.multi_tenancy.tenant.policy.AuthenticationDeviceRule;
import org.idp.server.platform.random.RandomStringGenerator;

/**
 * Issues device secrets for authentication devices based on tenant policy.
 *
 * <p>When tenant's AuthenticationDeviceRule has issueDeviceSecret=true, this class generates a
 * secure random secret and attaches it as a jwt_bearer_symmetric credential to the device.
 *
 * <p>The issued secret can be used for JWT Bearer Grant (RFC 7523) authentication.
 */
public class DeviceSecretIssuer {

  /**
   * Returns the minimum secret byte length for the given HMAC algorithm per OIDC Core Section
   * 16.19.
   *
   * <ul>
   *   <li>HS256: 32 bytes (256 bits)
   *   <li>HS384: 48 bytes (384 bits)
   *   <li>HS512: 64 bytes (512 bits)
   * </ul>
   */
  private static int getSecretBytesForAlgorithm(String algorithm) {
    return switch (algorithm) {
      case "HS384" -> 48;
      case "HS512" -> 64;
      default -> 32; // HS256 or fallback
    };
  }

  /**
   * Issues device secret if configured in the device rule.
   *
   * @param device the authentication device to attach the secret to
   * @param deviceRule the tenant's device rule configuration
   * @return result containing the device (with or without credential) and optional secret
   */
  public DeviceSecretIssuanceResult issue(
      AuthenticationDevice device, AuthenticationDeviceRule deviceRule) {

    if (deviceRule == null || !deviceRule.issueDeviceSecret()) {
      return DeviceSecretIssuanceResult.noSecret(device);
    }

    String algorithm = deviceRule.deviceSecretAlgorithm();
    int secretBytes = getSecretBytesForAlgorithm(algorithm);
    String deviceSecret = new RandomStringGenerator(secretBytes).generate();

    Map<String, Object> credentialPayload = new HashMap<>();
    credentialPayload.put("algorithm", algorithm);
    credentialPayload.put("secret_value", deviceSecret);

    Map<String, Object> credentialMetadata = new HashMap<>();
    credentialMetadata.put("issued_at", System.currentTimeMillis());
    if (deviceRule.hasDeviceSecretExpiration()) {
      long expiresAtMillis =
          System.currentTimeMillis() + (deviceRule.deviceSecretExpiresInSeconds() * 1000);
      credentialMetadata.put("expires_at", expiresAtMillis);
    }

    AuthenticationDevice deviceWithCredential =
        device.withCredential(
            "jwt_bearer_symmetric",
            UUID.randomUUID().toString(),
            credentialPayload,
            credentialMetadata);

    return DeviceSecretIssuanceResult.withSecret(deviceWithCredential, deviceSecret, algorithm);
  }

  /**
   * Result of device secret issuance.
   *
   * @param device the authentication device (with or without credential attached)
   * @param deviceSecret the generated secret (null if not issued)
   * @param algorithm the signing algorithm (null if not issued)
   */
  public record DeviceSecretIssuanceResult(
      AuthenticationDevice device, String deviceSecret, String algorithm) {

    public static DeviceSecretIssuanceResult noSecret(AuthenticationDevice device) {
      return new DeviceSecretIssuanceResult(device, null, null);
    }

    public static DeviceSecretIssuanceResult withSecret(
        AuthenticationDevice device, String deviceSecret, String algorithm) {
      return new DeviceSecretIssuanceResult(device, deviceSecret, algorithm);
    }

    public boolean hasDeviceSecret() {
      return deviceSecret != null && !deviceSecret.isEmpty();
    }
  }
}
