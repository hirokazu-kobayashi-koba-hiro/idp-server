package org.idp.server.core.function;

import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;

public interface UserinfoFunction {

  UserinfoRequestResponse request(
      TenantIdentifier tenantId, String authorizationHeader, String clientCert);
}
