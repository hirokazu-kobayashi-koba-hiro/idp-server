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

package org.idp.server.core.openid.federation.repository;

import java.util.List;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.FederationConfigurationIdentifier;
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface FederationConfigurationQueryRepository {

  <T> T get(Tenant tenant, FederationType federationType, SsoProvider ssoProvider, Class<T> clazz);

  FederationConfiguration find(Tenant tenant, FederationConfigurationIdentifier identifier);

  FederationConfiguration findWithDisabled(
      Tenant tenant, FederationConfigurationIdentifier identifier, boolean includeDisabled);

  List<FederationConfiguration> findList(Tenant tenant, int limit, int offset);
}
