package org.idp.server.core.adapters.datasource.grantmanagment;

import java.util.Map;
import org.idp.server.core.grantmangment.AuthorizationGranted;
import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.basic.type.oauth.RequestedClientId;

public interface AuthorizationGrantedSqlExecutor {

  void insert(AuthorizationGranted authorizationGranted);

  Map<String, String> selectOne(
      TenantIdentifier tenantIdentifier, RequestedClientId requestedClientId, User user);

  void update(AuthorizationGranted authorizationGranted);
}
