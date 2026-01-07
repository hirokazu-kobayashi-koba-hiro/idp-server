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
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Delegate interface for JWT Bearer grant user lookup.
 *
 * <p>This delegate allows the Core layer to retrieve user information from the UseCase layer during
 * JWT Bearer grant processing, following the Hexagonal Architecture principle.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC 7523</a>
 */
public interface JwtBearerUserFindingDelegate {

  /**
   * Finds a user by tenant, issuer, subject, and claim mapping.
   *
   * <p>The implementation should use the subjectClaimMapping to determine which JWT claim value to
   * use for user lookup. For example:
   *
   * <ul>
   *   <li>"sub" - use the JWT sub claim directly as user ID
   *   <li>"email" - lookup user by email address from JWT claims
   * </ul>
   *
   * @param tenant the tenant context
   * @param issuer the JWT issuer (iss claim)
   * @param subject the JWT subject (sub claim)
   * @param subjectClaimMapping the claim mapping configuration (e.g., "email", "sub")
   * @return the user object (may be empty if not found)
   */
  User findUser(Tenant tenant, String issuer, String subject, String subjectClaimMapping);
}
