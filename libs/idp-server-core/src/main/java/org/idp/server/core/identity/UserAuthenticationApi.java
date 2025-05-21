package org.idp.server.core.identity;

import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface UserAuthenticationApi {

  Pairs<User, OAuthToken> authenticate(
      TenantIdentifier adminTenantIdentifier, String authorizationHeader);
}
