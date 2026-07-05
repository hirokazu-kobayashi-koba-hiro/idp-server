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

package org.idp.server.core.extension.ciba.repository;

import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.ciba.AuthReqId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface CibaGrantRepository {

  void register(Tenant tenant, CibaGrant cibaGrant);

  void update(Tenant tenant, CibaGrant cibaGrant);

  CibaGrant find(Tenant tenant, AuthReqId authReqId);

  /**
   * Locks the CIBA grant row for the current transaction (SELECT ... FOR UPDATE) and scopes the
   * lookup by tenant.
   *
   * <p>Used by the CIBA token issuance to enforce single-use: a concurrent poll of the same
   * auth_req_id blocks until this transaction commits (which deletes the grant), then finds no row
   * and is rejected. The tenant predicate is the sole tenant boundary on MySQL (no RLS there).
   */
  CibaGrant findForUpdate(Tenant tenant, AuthReqId authReqId);

  CibaGrant get(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier);

  void delete(Tenant tenant, CibaGrant cibaGrant);
}
