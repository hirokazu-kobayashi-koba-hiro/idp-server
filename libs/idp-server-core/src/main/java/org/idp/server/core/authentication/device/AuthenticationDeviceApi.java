package org.idp.server.core.authentication.device;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface AuthenticationDeviceApi {

  AuthenticationTransactionFindingResponse findLatest(
      TenantIdentifier tenantIdentifier,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      RequestAttributes requestAttributes);
}
