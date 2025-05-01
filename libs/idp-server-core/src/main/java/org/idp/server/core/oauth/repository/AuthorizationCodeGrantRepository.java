package org.idp.server.core.oauth.repository;

import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.basic.type.oauth.AuthorizationCode;

public interface AuthorizationCodeGrantRepository {
  void register(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant);

  AuthorizationCodeGrant find(Tenant tenant, AuthorizationCode authorizationCode);

  void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant);
}
