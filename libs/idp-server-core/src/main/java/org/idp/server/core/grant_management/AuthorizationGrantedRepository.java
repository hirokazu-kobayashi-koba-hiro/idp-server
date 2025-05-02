package org.idp.server.core.grant_management;

import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthorizationGrantedRepository {

  void register(Tenant tenant, AuthorizationGranted authorizationGranted);

  AuthorizationGranted find(Tenant tenant, RequestedClientId requestedClientId, User user);

  void update(Tenant tenant, AuthorizationGranted authorizationGranted);
}
