/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.usecases.application.enduser;

import org.idp.server.authentication.interactors.device.AuthenticationDeviceApi;
import org.idp.server.authentication.interactors.device.AuthenticationTransactionFindingResponse;
import org.idp.server.core.oidc.authentication.AuthenticationTransaction;
import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.type.RequestAttributes;

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
