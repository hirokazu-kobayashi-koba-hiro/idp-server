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

package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionQueries;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationTransactionQuerySqlExecutor {

  Map<String, String> selectOne(Tenant tenant, AuthenticationTransactionIdentifier identifier);

  Map<String, String> selectOne(Tenant tenant, AuthorizationIdentifier identifier);

  Map<String, String> selectOneByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier authenticationDeviceIdentifier);

  List<Map<String, String>> selectList(Tenant tenant, AuthenticationTransactionQueries queries);
}
