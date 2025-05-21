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
