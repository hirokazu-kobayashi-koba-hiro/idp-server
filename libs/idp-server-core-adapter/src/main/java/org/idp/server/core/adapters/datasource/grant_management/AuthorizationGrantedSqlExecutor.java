package org.idp.server.core.adapters.datasource.grant_management;

import java.util.Map;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.grant_management.AuthorizationGranted;
import org.idp.server.core.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface AuthorizationGrantedSqlExecutor {

  void insert(AuthorizationGranted authorizationGranted);

  Map<String, String> selectOne(
      TenantIdentifier tenantIdentifier, RequestedClientId requestedClientId, User user);

  void update(AuthorizationGranted authorizationGranted);
}
