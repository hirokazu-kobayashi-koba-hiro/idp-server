package org.idp.server.core.authentication;

import org.idp.server.core.oauth.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.tenant.Tenant;

public interface AuthenticationTransactionQueryRepository {

  AuthenticationTransaction get(Tenant tenant, AuthorizationIdentifier identifier);

  AuthenticationTransaction findLatest(
      Tenant tenant, AuthenticationDeviceIdentifier authenticationDeviceIdentifier);
}
