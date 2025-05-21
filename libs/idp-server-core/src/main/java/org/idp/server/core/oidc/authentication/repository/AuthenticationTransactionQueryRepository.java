package org.idp.server.core.oidc.authentication.repository;

import org.idp.server.core.oidc.authentication.AuthenticationTransaction;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationTransactionQueryRepository {

  AuthenticationTransaction get(Tenant tenant, AuthorizationIdentifier identifier);

  AuthenticationTransaction findLatest(
      Tenant tenant, AuthenticationDeviceIdentifier authenticationDeviceIdentifier);
}
