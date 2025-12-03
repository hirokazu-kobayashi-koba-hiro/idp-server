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

package org.idp.server.core.openid.token;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.Subject;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Delegate interface for refresh token grant user lookup.
 *
 * <p>This delegate allows the Core layer to retrieve current user information from the UseCase
 * layer during refresh token validation, following the Hexagonal Architecture principle.
 *
 * @see org.idp.server.core.openid.token.verifier.RefreshTokenVerifier
 */
public interface TokenUserFindingDelegate {

  /**
   * Finds a user by tenant and subject.
   *
   * @param tenant the tenant context
   * @param subject the user's subject identifier
   * @return the user object (may be empty if not found)
   */
  User findUser(Tenant tenant, Subject subject);
}
