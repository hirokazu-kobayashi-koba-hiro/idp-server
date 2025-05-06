package org.idp.server.core.identity;

import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;

public interface UserAuthenticationApi {

  Pairs<User, OAuthToken> authenticate(
      TenantIdentifier adminTenantIdentifier, String authorizationHeader);
}
