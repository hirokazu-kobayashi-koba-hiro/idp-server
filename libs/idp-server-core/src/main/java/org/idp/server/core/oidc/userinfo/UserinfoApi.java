package org.idp.server.core.oidc.userinfo;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface UserinfoApi {

  UserinfoRequestResponse request(
      TenantIdentifier tenantId,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes);
}
