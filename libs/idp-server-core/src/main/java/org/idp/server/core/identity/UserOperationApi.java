package org.idp.server.core.identity;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface UserOperationApi {

  void delete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes);
}
