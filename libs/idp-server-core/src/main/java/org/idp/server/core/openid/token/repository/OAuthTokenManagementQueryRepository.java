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

package org.idp.server.core.openid.token.repository;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.token.OAuthTokenIdentifier;
import org.idp.server.core.openid.token.OAuthTokenQueries;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface OAuthTokenManagementQueryRepository {

  Map<String, String> get(Tenant tenant, OAuthTokenIdentifier identifier);

  List<Map<String, String>> findList(Tenant tenant, OAuthTokenQueries queries);

  long findTotalCount(Tenant tenant, OAuthTokenQueries queries);

  long countByUser(Tenant tenant, String userId);
}
