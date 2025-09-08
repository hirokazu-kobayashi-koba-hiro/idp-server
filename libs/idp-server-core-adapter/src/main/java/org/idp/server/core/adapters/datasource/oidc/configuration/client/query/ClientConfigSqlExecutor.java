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

package org.idp.server.core.adapters.datasource.oidc.configuration.client.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientIdentifier;
import org.idp.server.core.openid.oauth.configuration.client.ClientQueries;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface ClientConfigSqlExecutor {

  void insert(Tenant tenant, ClientConfiguration clientConfiguration);

  Map<String, String> selectByAlias(Tenant tenant, RequestedClientId requestedClientId);

  Map<String, String> selectByAlias(
      Tenant tenant, RequestedClientId requestedClientId, boolean includeDisabled);

  Map<String, String> selectById(Tenant tenant, ClientIdentifier clientIdentifier);

  Map<String, String> selectById(
      Tenant tenant, ClientIdentifier clientIdentifier, boolean includeDisabled);

  List<Map<String, String>> selectList(Tenant tenant, int limit, int offset);

  List<Map<String, String>> selectList(Tenant tenant, ClientQueries queries);

  long selectTotalCount(Tenant tenant, ClientQueries queries);

  List<Map<String, String>> selectList(
      Tenant tenant, int limit, int offset, boolean includeDisabled);

  void update(Tenant tenant, ClientConfiguration clientConfiguration);

  void delete(Tenant tenant, RequestedClientId requestedClientId);
}
