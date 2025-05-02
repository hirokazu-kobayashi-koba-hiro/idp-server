package org.idp.server.core.authentication.repository;

import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationTransactionQueryRepository {

  AuthenticationTransaction get(Tenant tenant, AuthorizationIdentifier identifier);

  AuthenticationTransaction findLatest(
      Tenant tenant, AuthenticationDeviceIdentifier authenticationDeviceIdentifier);
}
