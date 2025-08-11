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

package org.idp.server.core.openid.authentication.repository;

import java.util.List;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.AuthenticationTransactionQueries;
import org.idp.server.core.openid.authentication.AuthorizationIdentifier;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationTransactionQueryRepository {

  AuthenticationTransaction get(Tenant tenant, AuthenticationTransactionIdentifier identifier);

  AuthenticationTransaction get(Tenant tenant, AuthorizationIdentifier identifier);

  List<AuthenticationTransaction> findList(
      Tenant tenant,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      AuthenticationTransactionQueries queries);

  long findTotalCount(
      Tenant tenant,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      AuthenticationTransactionQueries queries);

  long findTotalCount(Tenant tenant, AuthenticationTransactionQueries queries);

  List<AuthenticationTransaction> findList(Tenant tenant, AuthenticationTransactionQueries queries);

  AuthenticationTransaction find(Tenant tenant, AuthenticationTransactionIdentifier identifier);
}
