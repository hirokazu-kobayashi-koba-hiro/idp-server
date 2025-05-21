package org.idp.server.core.oidc.identity;

import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface UserAuthenticationApi {

  Pairs<User, OAuthToken> authenticate(
      TenantIdentifier adminTenantIdentifier, String authorizationHeader);
}
