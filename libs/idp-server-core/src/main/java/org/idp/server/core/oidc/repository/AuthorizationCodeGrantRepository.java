package org.idp.server.core.oidc.repository;

import org.idp.server.basic.type.oauth.AuthorizationCode;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;

public interface AuthorizationCodeGrantRepository {
  void register(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant);

  AuthorizationCodeGrant find(Tenant tenant, AuthorizationCode authorizationCode);

  void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant);
}
