package org.idp.server.core.admin;

import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.extension.Pairs;

public interface OperatorAuthenticationApi {

  Pairs<User, OAuthToken> authenticate(
      TenantIdentifier adminTenantIdentifier, String authorizationHeader);
}
