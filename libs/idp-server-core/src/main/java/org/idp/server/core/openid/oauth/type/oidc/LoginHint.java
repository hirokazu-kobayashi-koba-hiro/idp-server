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

package org.idp.server.core.openid.oauth.type.oidc;

import java.util.Objects;
import java.util.Optional;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;

/**
 * login_hint OPTIONAL.
 *
 * <p>Hint to the Authorization Server about the login identifier the End-User might use to log in
 * (if necessary). This hint can be used by an RP if it first asks the End-User for their e-mail
 * address (or other identifier) and then wants to pass that value as a hint to the discovered
 * authorization service. It is RECOMMENDED that the hint value match the value used for discovery.
 * This value MAY also be a phone number in the format specified for the phone_number Claim. The use
 * of this parameter is left to the OP's discretion.
 *
 * <h2>Supported Formats</h2>
 *
 * <p>This implementation supports the following prefixed login hint formats:
 *
 * <table border="1">
 *   <caption>Login Hint Formats</caption>
 *   <tr>
 *     <th>Format</th>
 *     <th>Description</th>
 *     <th>Example</th>
 *   </tr>
 *   <tr>
 *     <td><code>device:{deviceId}</code></td>
 *     <td>Device identifier for CIBA authentication</td>
 *     <td><code>device:550e8400-e29b-41d4-a716-446655440000</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>sub:{subject}</code></td>
 *     <td>Subject identifier (user ID)</td>
 *     <td><code>sub:1234567890</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>ex-sub:{subject}</code></td>
 *     <td>External IdP subject identifier</td>
 *     <td><code>ex-sub:google-user-123</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>email:{email}</code></td>
 *     <td>Email address</td>
 *     <td><code>email:user@example.com</code></td>
 *   </tr>
 *   <tr>
 *     <td><code>phone:{phoneNumber}</code></td>
 *     <td>Phone number</td>
 *     <td><code>phone:+81-90-1234-5678</code></td>
 *   </tr>
 * </table>
 *
 * <h2>Additional Provider Hint</h2>
 *
 * <p>All formats support an optional IdP provider hint using comma separation:
 *
 * <pre>{@code
 * device:{deviceId},idp:{providerId}
 * email:{email},idp:{providerId}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li><code>device:550e8400-e29b-41d4-a716-446655440000,idp:google</code>
 *   <li><code>email:user@example.com,idp:azure-ad</code>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>{@code
 * LoginHint loginHint = new LoginHint("device:550e8400-e29b-41d4-a716-446655440000");
 *
 * // Type-safe extraction
 * Optional<AuthenticationDeviceIdentifier> deviceId = loginHint.asDeviceIdentifier();
 * deviceId.ifPresent(id -> {
 *   // Use device identifier for CIBA authentication
 *   AuthenticationDevice device = user.findAuthenticationDevice(id.value());
 * });
 *
 * // Type checking
 * LoginHintType type = loginHint.getType(); // Returns LoginHintType.DEVICE
 *
 * // String-based extraction
 * if (loginHint.hasPrefix("device:")) {
 *   String deviceIdStr = loginHint.extractPrefixValue("device:");
 * }
 * }</pre>
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 * @see LoginHintType
 * @see org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier
 */
public class LoginHint {
  String value;

  public LoginHint() {}

  public LoginHint(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  /**
   * Checks if the login hint starts with the specified prefix.
   *
   * @param prefix the prefix to check (e.g., "device:", "email:", "sub:")
   * @return true if the login hint starts with the prefix
   */
  public boolean hasPrefix(String prefix) {
    return Objects.nonNull(value) && value.startsWith(prefix);
  }

  /**
   * Extracts the value after the specified prefix from the login hint.
   *
   * <p>Supports formats like:
   *
   * <ul>
   *   <li>"prefix:value" → returns "value"
   *   <li>"prefix:value,idp:providerId" → returns "value" (ignores additional hints)
   * </ul>
   *
   * @param prefix the prefix to extract from (e.g., "device:", "email:", "sub:")
   * @return the extracted value, or empty string if prefix not found
   */
  public String extractPrefixValue(String prefix) {
    if (!hasPrefix(prefix)) {
      return "";
    }
    // Handle "prefix:value" or "prefix:value,idp:providerId"
    String[] parts = value.split(",");
    return parts[0].substring(prefix.length());
  }

  /**
   * Checks if the login hint specifies a device ID.
   *
   * @return true if the login hint starts with "device:"
   */
  public boolean isDeviceHint() {
    return hasPrefix("device:");
  }

  /**
   * Extracts the device ID from the login hint.
   *
   * <p>Supports formats like:
   *
   * <ul>
   *   <li>"device:{deviceId}" → returns "{deviceId}"
   *   <li>"device:{deviceId},idp:{providerId}" → returns "{deviceId}"
   * </ul>
   *
   * @return the device ID, or empty string if not a device hint
   */
  public String extractDeviceId() {
    return extractPrefixValue("device:");
  }

  /**
   * Returns the type of this login hint based on its prefix.
   *
   * @return the login hint type
   */
  public LoginHintType getType() {
    if (hasPrefix("device:")) return LoginHintType.DEVICE;
    if (hasPrefix("sub:")) return LoginHintType.SUBJECT;
    if (hasPrefix("ex-sub:")) return LoginHintType.EXTERNAL_SUBJECT;
    if (hasPrefix("email:")) return LoginHintType.EMAIL;
    if (hasPrefix("phone:")) return LoginHintType.PHONE;
    return LoginHintType.UNKNOWN;
  }

  /**
   * Extracts device identifier if this is a device hint.
   *
   * <p>Returns empty if:
   *
   * <ul>
   *   <li>Login hint is not a device hint (doesn't start with "device:")
   *   <li>Device ID is empty after extraction
   * </ul>
   *
   * @return AuthenticationDeviceIdentifier wrapped in Optional, or empty if not a device hint
   */
  public Optional<AuthenticationDeviceIdentifier> asDeviceIdentifier() {
    if (!isDeviceHint()) {
      return Optional.empty();
    }
    String deviceId = extractDeviceId();
    if (deviceId.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new AuthenticationDeviceIdentifier(deviceId));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LoginHint that = (LoginHint) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
