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

import org.idp.server.authentication.interactors.fidouaf.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

@Transaction(readOnly = true)
public class AuthenticationMetaDataEntryService implements AuthenticationMetaDataApi {

  AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository;
  FidoUafExecutors fidoUafExecutors;
  TenantQueryRepository tenantQueryRepository;

  public AuthenticationMetaDataEntryService(
      AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository,
      FidoUafExecutors fidoUafExecutors,
      TenantQueryRepository tenantQueryRepository) {
    this.authenticationConfigurationQueryRepository = authenticationConfigurationQueryRepository;
    this.fidoUafExecutors = fidoUafExecutors;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public FidoUafExecutionResult getFidoUafFacets(TenantIdentifier tenantIdentifier) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    FidoUafConfiguration fidoUafConfiguration =
        authenticationConfigurationQueryRepository.get(
            tenant, "fido-uaf", FidoUafConfiguration.class);

    FidoUafExecutor fidoUafExecutor = fidoUafExecutors.get(fidoUafConfiguration.type());

    return fidoUafExecutor.getFidoUafFacets(tenant, fidoUafConfiguration);
  }
}
