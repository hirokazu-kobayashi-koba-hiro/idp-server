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
import java.util.Set;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * NoOpOIDCSessionCoordinator
 *
 * <p>A no-operation implementation of OIDCSessionCoordinator that does nothing. Used when OIDC
 * Session Management is not configured, eliminating null checks in calling code.
 */
public class NoOpOIDCSessionCoordinator extends OIDCSessionCoordinator {

  private static final NoOpOIDCSessionCoordinator INSTANCE = new NoOpOIDCSessionCoordinator();

  private NoOpOIDCSessionCoordinator() {
    super(null);
  }

  public static NoOpOIDCSessionCoordinator getInstance() {
    return INSTANCE;
  }

  @Override
  public OPSession onAuthenticationSuccess(
      Tenant tenant, User user, Authentication authentication) {
    return null;
  }

  @Override
  public String computeSessionHash(String opSessionId) {
    return "";
  }

  @Override
  public Optional<OPSession> getOPSession(Tenant tenant, String opSessionId) {
    return Optional.empty();
  }

  @Override
  public boolean isSessionValid(OPSession opSession, Long maxAge) {
    return false;
  }

  @Override
  public ClientSessionIdentifier onAuthorize(
      Tenant tenant, OPSession opSession, String clientId, Set<String> scopes, String nonce) {
    return new ClientSessionIdentifier();
  }

  @Override
  public Optional<OPSessionIdentifier> findOPSessionBySid(
      Tenant tenant, ClientSessionIdentifier sid) {
    return Optional.empty();
  }

  @Override
  public OIDCSessionManager sessionManager() {
    return null;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void registerSessionCookies(
      Tenant tenant, OPSession opSession, SessionCookieDelegate sessionCookieDelegate) {
    // No-op
  }

  @Override
  public Optional<OPSession> getOPSessionFromCookie(
      Tenant tenant, SessionCookieDelegate sessionCookieDelegate) {
    return Optional.empty();
  }
}
