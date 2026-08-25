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

package org.idp.server.core.openid.session;

/**
 * SessionSwitchPolicy
 *
 * <p>Defines the behavior when a different user attempts to authenticate in a browser that already
 * has an active session.
 *
 * <p>This policy controls how the IdP handles user switching scenarios:
 *
 * <ul>
 *   <li>Enterprise/Financial applications may require STRICT mode to prevent unauthorized access
 *   <li>Consumer applications may prefer SWITCH_ALLOWED for better UX on shared devices
 *   <li>MULTI_SESSION maintains backward compatibility but may cause orphan sessions
 * </ul>
 */
public enum SessionSwitchPolicy {

  /**
   * Strict mode.
   *
   * <p>When a different user attempts to authenticate while another user's session is active, an
   * error is returned. The user must explicitly logout first before switching accounts.
   *
   * <p>Use case: Enterprise, financial applications requiring strict session control.
   */
  STRICT,

  /**
   * Switch allowed mode. This is the default.
   *
   * <p>When a different user authenticates, the existing session is terminated and a new session is
   * created for the new user. This prevents orphan sessions while allowing user switching.
   *
   * <p>Use case: Consumer applications, shared devices, kiosks.
   */
  SWITCH_ALLOWED,

  /**
   * Multi-session mode.
   *
   * <p>Always creates a new session regardless of existing sessions. The old session remains until
   * TTL expiration. This may cause orphan sessions but provides maximum flexibility.
   *
   * <p>Use case: Applications requiring multiple concurrent sessions per browser.
   */
  MULTI_SESSION;

  public static SessionSwitchPolicy of(String value) {
    if (value == null || value.isEmpty()) {
      return SWITCH_ALLOWED;
    }
    try {
      return valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return SWITCH_ALLOWED;
    }
  }

  public boolean isStrict() {
    return this == STRICT;
  }

  public boolean isSwitchAllowed() {
    return this == SWITCH_ALLOWED;
  }

  public boolean isMultiSession() {
    return this == MULTI_SESSION;
  }
}
