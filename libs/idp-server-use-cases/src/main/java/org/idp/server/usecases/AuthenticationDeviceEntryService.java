package org.idp.server.usecases;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.device.AuthenticationDeviceApi;
import org.idp.server.core.authentication.device.AuthenticationTransactionFindingResponse;
import org.idp.server.core.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;

@Transaction
public class AuthenticationDeviceEntryService implements AuthenticationDeviceApi {

  TenantRepository tenantRepository;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;

  public AuthenticationDeviceEntryService(
      TenantRepository tenantRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository) {
    this.tenantRepository = tenantRepository;
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
  }

  public AuthenticationTransactionFindingResponse findLatest(
      TenantIdentifier tenantIdentifier,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.findLatest(tenant, authenticationDeviceIdentifier);

    if (!authenticationTransaction.exists()) {
      return AuthenticationTransactionFindingResponse.notFound();
    }

    return AuthenticationTransactionFindingResponse.success(authenticationTransaction);
  }
}
