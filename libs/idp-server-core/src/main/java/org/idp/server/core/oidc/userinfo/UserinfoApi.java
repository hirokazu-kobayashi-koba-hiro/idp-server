package org.idp.server.core.oidc.userinfo;

import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;

public interface UserinfoApi {

  UserinfoRequestResponse request(
      TenantIdentifier tenantId,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes);
}
