package org.idp.server.core.authentication.device;

import org.idp.server.core.oauth.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.security.RequestAttributes;

public interface AuthenticationDeviceApi {

  AuthenticationTransactionFindingResponse findLatest(
      TenantIdentifier tenantIdentifier,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      RequestAttributes requestAttributes);
}
