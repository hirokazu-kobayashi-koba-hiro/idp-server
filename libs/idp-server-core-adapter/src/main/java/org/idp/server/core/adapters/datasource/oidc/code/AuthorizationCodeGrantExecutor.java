package org.idp.server.core.adapters.datasource.oidc.code;

import java.util.Map;
import org.idp.server.basic.type.oauth.AuthorizationCode;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthorizationCodeGrantExecutor {

  void insert(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant);

  Map<String, String> selectOne(Tenant tenant, AuthorizationCode authorizationCode);

  void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant);
}
