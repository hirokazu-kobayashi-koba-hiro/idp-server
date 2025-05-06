package org.idp.server.core.identity;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;

public interface UserApi {

  void delete(TenantIdentifier tenantIdentifier, User user, OAuthToken oAuthToken, RequestAttributes requestAttributes);
}
