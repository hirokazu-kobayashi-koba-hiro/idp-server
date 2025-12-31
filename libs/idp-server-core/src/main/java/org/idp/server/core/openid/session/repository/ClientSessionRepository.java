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

package org.idp.server.core.openid.session.repository;

import java.util.Optional;
import org.idp.server.core.openid.session.ClientSession;
import org.idp.server.core.openid.session.ClientSessionIdentifier;
import org.idp.server.core.openid.session.ClientSessions;
import org.idp.server.core.openid.session.OPSessionIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface ClientSessionRepository {

  void save(Tenant tenant, ClientSession session);

  Optional<ClientSession> findBySid(Tenant tenant, ClientSessionIdentifier sid);

  ClientSessions findByOpSessionId(Tenant tenant, OPSessionIdentifier opSessionId);

  ClientSessions findByTenantAndSub(TenantIdentifier tenantId, String sub);

  ClientSessions findByTenantClientAndSub(TenantIdentifier tenantId, String clientId, String sub);

  void deleteBySid(Tenant tenant, ClientSessionIdentifier sid);

  int deleteByOpSessionId(Tenant tenant, OPSessionIdentifier opSessionId);
}
