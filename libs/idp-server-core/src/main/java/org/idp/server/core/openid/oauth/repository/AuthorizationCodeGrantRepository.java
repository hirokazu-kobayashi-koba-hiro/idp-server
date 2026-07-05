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

package org.idp.server.core.openid.oauth.repository;

import org.idp.server.core.openid.grant_management.grant.AuthorizationCodeGrant;
import org.idp.server.core.openid.oauth.type.oauth.AuthorizationCode;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthorizationCodeGrantRepository {
  void register(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant);

  AuthorizationCodeGrant find(Tenant tenant, AuthorizationCode authorizationCode);

  /**
   * Locks the authorization code grant row for the duration of the current transaction (SELECT ...
   * FOR UPDATE).
   *
   * <p>Used by the authorization_code token exchange to enforce single-use: a concurrent exchange
   * of the same code blocks until this transaction commits (which deletes the row), then finds no
   * row and is rejected. Prevents the find-verify-delete race that would otherwise issue two token
   * sets from one code.
   */
  AuthorizationCodeGrant findForUpdate(Tenant tenant, AuthorizationCode authorizationCode);

  void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant);
}
