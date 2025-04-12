package org.idp.server.core.userinfo;

import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.userinfo.handler.io.UserinfoRequestResponse;

public interface UserinfoApi {

  UserinfoRequestResponse request(
      TenantIdentifier tenantId, String authorizationHeader, String clientCert);
}
