/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
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
