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

import java.util.Optional;

/**
 * AuthSessionCookieDelegate
 *
 * <p>Handles authentication session cookie operations to prevent authorization flow hijacking
 * attacks (session fixation attack variant).
 *
 * <p>This cookie binds the browser session to the authorization transaction, ensuring that only the
 * browser that initiated the authorization request can complete the authentication flow.
 *
 * <p><b>Security Purpose:</b>
 *
 * <pre>
 * Without this cookie, an attacker could:
 * 1. Start an authorization request → get id=abc123
 * 2. Send URL with id=abc123 to victim
 * 3. Victim authenticates on that page (enters victim's credentials)
 * 4. Attacker completes authorization with id=abc123 → logs in as victim
 * </pre>
 *
 * <p><b>Similar to:</b> Keycloak's AUTH_SESSION_ID cookie
 *
 * @see <a href="https://owasp.org/www-community/attacks/Session_fixation">Session Fixation
 *     Attack</a>
 */
public interface AuthSessionCookieDelegate {

  /**
   * Sets the authentication session cookie when authorization request is created.
   *
   * <p>The cookie path is set to /{tenantId}/ to prevent cookie conflicts in federation flows where
   * multiple tenants on the same domain would otherwise overwrite each other's cookies.
   *
   * @param tenantId tenant identifier for scoping the cookie path
   * @param authSessionId unique identifier for this authorization session
   * @param maxAgeSeconds cookie max age in seconds (should match authorization request expiry)
   */
  void setAuthSessionCookie(String tenantId, String authSessionId, long maxAgeSeconds);

  /**
   * Gets the authentication session ID from AUTH_SESSION cookie.
   *
   * @return authentication session ID, or empty if not present
   */
  Optional<String> getAuthSessionId();

  /**
   * Clears the authentication session cookie. Should be called after authorization is complete
   * (success or failure).
   *
   * @param tenantId tenant identifier for scoping the cookie path
   */
  void clearAuthSessionCookie(String tenantId);
}
