package org.idp.server.core.grantmangment;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;

public interface AuthorizationGrantedRepository {

  void register(AuthorizationGranted authorizationGranted);

  AuthorizationGranted get(AuthorizationGrantedIdentifier identifier);

  AuthorizationGranted find(AuthorizationGrantedIdentifier identifier);

  AuthorizationGranted find(
      TenantIdentifier tenantIdentifier, RequestedClientId requestedClientId, User user);

  void update(AuthorizationGranted authorizationGranted);
}
