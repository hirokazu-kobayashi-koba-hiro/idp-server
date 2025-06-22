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

package org.idp.server.core.oidc.authentication.repository;

import java.util.List;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.oidc.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.oidc.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationInteractionQueryRepository {

  <T> T get(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String key, Class<T> clazz);

  long findTotalCount(Tenant tenant, AuthenticationInteractionQueries queries);

  List<AuthenticationInteraction> findList(Tenant tenant, AuthenticationInteractionQueries queries);

  AuthenticationInteraction find(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String key);
}
