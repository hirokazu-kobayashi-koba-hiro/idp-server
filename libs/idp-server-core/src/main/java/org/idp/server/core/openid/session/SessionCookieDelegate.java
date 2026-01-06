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
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * SessionCookieDelegate
 *
 * <p>Handles session cookie operations for OIDC Session Management. Similar to Keycloak's cookie
 * management pattern.
 *
 * <p>Two cookies are managed:
 *
 * <ul>
 *   <li>IDP_IDENTITY: JWT containing opSessionId, sub, authTime (HttpOnly)
 *   <li>IDP_SESSION: SHA256(opSessionId) for OIDC Session Management iframe (not HttpOnly)
 * </ul>
 *
 * @see <a href="https://openid.net/specs/openid-connect-session-1_0.html">OIDC Session
 *     Management</a>
 */
public interface SessionCookieDelegate {

  /**
   * Sets session cookies after successful authentication.
   *
   * @param tenant tenant for cookie path scoping and session configuration
   * @param identityToken JWT containing session information
   * @param sessionHash SHA256 hash of opSessionId for session management iframe
   * @param maxAgeSeconds cookie max age in seconds
   */
  void registerSessionCookies(
      Tenant tenant, String identityToken, String sessionHash, long maxAgeSeconds);

  /**
   * Gets the identity token from IDP_IDENTITY cookie.
   *
   * @return identity token JWT, or empty if not present
   */
  Optional<String> getIdentityToken();

  /**
   * Gets the session hash from IDP_SESSION cookie.
   *
   * @return session hash, or empty if not present
   */
  Optional<String> getSessionHash();

  /**
   * Clears session cookies on logout.
   *
   * @param tenant tenant for cookie path scoping
   */
  void clearSessionCookies(Tenant tenant);
}
