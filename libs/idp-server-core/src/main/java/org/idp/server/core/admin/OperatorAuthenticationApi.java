package org.idp.server.core.admin;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.extension.Pairs;

public interface OperatorAuthenticationApi {

  Pairs<User, String> authenticate(
      TenantIdentifier adminTenantIdentifier, String authorizationHeader);
}
