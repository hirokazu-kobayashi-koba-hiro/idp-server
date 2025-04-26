package org.idp.server.core;

import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.AuthenticationTransactionQueryRepository;
import org.idp.server.core.authentication.device.AuthenticationDeviceApi;
import org.idp.server.core.authentication.device.AuthenticationTransactionFindingResponse;
import org.idp.server.core.basic.datasource.Transaction;
import org.idp.server.core.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.security.RequestAttributes;

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
