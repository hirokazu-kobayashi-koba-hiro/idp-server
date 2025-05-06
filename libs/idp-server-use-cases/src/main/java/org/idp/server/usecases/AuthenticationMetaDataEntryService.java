package org.idp.server.usecases;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.authentication.AuthenticationMetaDataApi;
import org.idp.server.core.authentication.fidouaf.*;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;

@Transaction
public class AuthenticationMetaDataEntryService implements AuthenticationMetaDataApi {

  AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository;
  FidoUafExecutors fidoUafExecutors;
  TenantRepository tenantRepository;

  public AuthenticationMetaDataEntryService(AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository, FidoUafExecutors fidoUafExecutors, TenantRepository tenantRepository) {
    this.authenticationConfigurationQueryRepository = authenticationConfigurationQueryRepository;
    this.fidoUafExecutors = fidoUafExecutors;
    this.tenantRepository = tenantRepository;
  }

  @Override
  public FidoUafExecutionResult getFidoUafFacets(TenantIdentifier tenantIdentifier) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);
    FidoUafConfiguration fidoUafConfiguration = authenticationConfigurationQueryRepository.get(tenant, "fido-uaf", FidoUafConfiguration.class);

    FidoUafExecutor fidoUafExecutor = fidoUafExecutors.get(fidoUafConfiguration.type());

    return fidoUafExecutor.getFidoUafFacets(tenant, fidoUafConfiguration);
  }
}
