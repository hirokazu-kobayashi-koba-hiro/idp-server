package org.idp.server.core;

import org.idp.server.core.authentication.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.authentication.AuthenticationMetaDataApi;
import org.idp.server.core.authentication.fidouaf.*;
import org.idp.server.core.basic.datasource.Transaction;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;

@Transaction
public class AuthenticationMetaDataEntryService implements AuthenticationMetaDataApi {

  AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository;
  FidoUafExecutors fidoUafExecutors;
  TenantRepository tenantRepository;

  public AuthenticationMetaDataEntryService(
      AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository,
      FidoUafExecutors fidoUafExecutors,
      TenantRepository tenantRepository) {
    this.authenticationConfigurationQueryRepository = authenticationConfigurationQueryRepository;
    this.fidoUafExecutors = fidoUafExecutors;
    this.tenantRepository = tenantRepository;
  }

  @Override
  public FidoUafExecutionResult getFidoUafFacets(TenantIdentifier tenantIdentifier) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);
    FidoUafConfiguration fidoUafConfiguration =
        authenticationConfigurationQueryRepository.get(
            tenant, "fido-uaf", FidoUafConfiguration.class);

    FidoUafExecutor fidoUafExecutor = fidoUafExecutors.get(fidoUafConfiguration.type());

    return fidoUafExecutor.getFidoUafFacets(tenant, fidoUafConfiguration);
  }
}
