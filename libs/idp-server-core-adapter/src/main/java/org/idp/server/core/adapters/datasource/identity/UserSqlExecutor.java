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

package org.idp.server.core.adapters.datasource.identity;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface UserSqlExecutor {

  Map<String, String> selectOne(Tenant tenant, UserIdentifier userIdentifier);

  Map<String, String> selectByExternalIdpSubject(
      Tenant tenant, String externalSubject, String providerId);

  Map<String, String> selectByName(Tenant tenant, String name, String providerId);

  Map<String, String> selectByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId, String providerId);

  Map<String, String> selectByEmail(Tenant tenant, String email, String providerId);

  Map<String, String> selectByPhone(Tenant tenant, String phone, String providerId);

  Map<String, String> selectCount(Tenant tenant, UserQueries queries);

  List<Map<String, String>> selectList(Tenant tenant, UserQueries queries);

  Map<String, String> selectByProvider(Tenant tenant, String providerId, String providerUserId);

  Map<String, String> selectByAuthenticationDevice(Tenant tenant, String deviceId);

  Map<String, String> selectAssignedOrganization(Tenant tenant, UserIdentifier userIdentifier);

  Map<String, String> selectAssignedTenant(Tenant tenant, UserIdentifier userIdentifier);
}
