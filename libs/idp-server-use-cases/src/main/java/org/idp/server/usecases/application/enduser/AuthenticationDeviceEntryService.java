package org.idp.server.usecases.application.enduser;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.device.AuthenticationDeviceApi;
import org.idp.server.core.authentication.device.AuthenticationTransactionFindingResponse;
import org.idp.server.core.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

@Transaction(readOnly = true)
public class AuthenticationDeviceEntryService implements AuthenticationDeviceApi {

  TenantQueryRepository tenantQueryRepository;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;

  public AuthenticationDeviceEntryService(
      TenantQueryRepository tenantQueryRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
  }

  public AuthenticationTransactionFindingResponse findLatest(
      TenantIdentifier tenantIdentifier,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.findLatest(tenant, authenticationDeviceIdentifier);

    if (!authenticationTransaction.exists()) {
      return AuthenticationTransactionFindingResponse.notFound();
    }

    return AuthenticationTransactionFindingResponse.success(authenticationTransaction);
  }
}
