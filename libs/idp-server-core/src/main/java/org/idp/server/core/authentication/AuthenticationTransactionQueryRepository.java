package org.idp.server.core.authentication;

import org.idp.server.core.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationTransactionQueryRepository {

  AuthenticationTransaction get(Tenant tenant, AuthorizationIdentifier identifier);

  AuthenticationTransaction findLatest(
      Tenant tenant, AuthenticationDeviceIdentifier authenticationDeviceIdentifier);
}
