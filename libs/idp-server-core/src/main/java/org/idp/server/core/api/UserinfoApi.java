package org.idp.server.core.api;

import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;

public interface UserinfoApi {

  UserinfoRequestResponse request(
      TenantIdentifier tenantId, String authorizationHeader, String clientCert);
}
