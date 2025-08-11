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

package org.idp.server.usecases.application.enduser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionApi;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.AuthenticationTransactionQueries;
import org.idp.server.core.openid.authentication.io.AuthenticationTransactionFindingResponse;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction(readOnly = true)
public class AuthenticationTransactionEntryService implements AuthenticationTransactionApi {

  TenantQueryRepository tenantQueryRepository;
  AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;

  public AuthenticationTransactionEntryService(
      TenantQueryRepository tenantQueryRepository,
      AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.authenticationTransactionCommandRepository = authenticationTransactionCommandRepository;
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
  }

  @Override
  public AuthenticationTransaction request(
      TenantIdentifier tenantIdentifier, RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuthenticationTransaction authenticationTransaction = new AuthenticationTransaction();
    authenticationTransactionCommandRepository.register(tenant, authenticationTransaction);

    return null;
  }

  public AuthenticationTransaction get(
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    return authenticationTransactionQueryRepository.get(
        tenant, authenticationTransactionIdentifier);
  }

  @Override
  public AuthenticationTransactionFindingResponse findList(
      TenantIdentifier tenantIdentifier,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      AuthenticationTransactionQueries queries,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    long totalCount =
        authenticationTransactionQueryRepository.findTotalCount(
            tenant, authenticationDeviceIdentifier, queries);

    if (totalCount == 0) {
      Map<String, Object> contents = new HashMap<>();
      contents.put("list", List.of());
      contents.put("total_count", totalCount);
      contents.put("limit", queries.limit());
      contents.put("offset", queries.offset());

      return AuthenticationTransactionFindingResponse.success(contents);
    }

    List<AuthenticationTransaction> authenticationTransactions =
        authenticationTransactionQueryRepository.findList(
            tenant, authenticationDeviceIdentifier, queries);

    Map<String, Object> contents = new HashMap<>();
    contents.put(
        "list",
        authenticationTransactions.stream().map(AuthenticationTransaction::toRequestMap).toList());
    contents.put("total_count", totalCount);
    contents.put("limit", queries.limit());
    contents.put("offset", queries.offset());

    return AuthenticationTransactionFindingResponse.success(contents);
  }
}
