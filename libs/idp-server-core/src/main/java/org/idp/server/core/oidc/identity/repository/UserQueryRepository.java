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

package org.idp.server.core.oidc.identity.repository;

import java.util.List;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.core.oidc.identity.UserQueries;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface UserQueryRepository {

  User get(Tenant tenant, UserIdentifier userIdentifier);

  User findById(Tenant tenant, UserIdentifier userIdentifier);

  User findByExternalIdpSubject(Tenant tenant, String hint, String providerId);

  User findByName(Tenant tenant, String hint, String providerId);

  User findByDeviceId(Tenant tenant, String hint, String providerId);

  User findByEmail(Tenant tenant, String hint, String providerId);

  User findByPhone(Tenant tenant, String hint, String providerId);

  long findTotalCount(Tenant tenant, UserQueries queries);

  List<User> findList(Tenant tenant, UserQueries queries);

  User findByProvider(Tenant tenant, String providerId, String providerUserId);

  User findByAuthenticationDevice(Tenant tenant, String deviceId);
}
