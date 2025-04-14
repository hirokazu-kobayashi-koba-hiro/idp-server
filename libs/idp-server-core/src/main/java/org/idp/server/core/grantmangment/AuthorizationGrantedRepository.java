package org.idp.server.core.grantmangment;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.RequestedClientId;

public interface AuthorizationGrantedRepository {

  void register(Tenant tenant, AuthorizationGranted authorizationGranted);

  AuthorizationGranted find(Tenant tenant, RequestedClientId requestedClientId, User user);

  void update(Tenant tenant, AuthorizationGranted authorizationGranted);
}
