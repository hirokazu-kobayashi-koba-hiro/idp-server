package org.idp.server.core.adapters.datasource.oidc.code;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.type.oauth.AuthorizationCode;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthorizationCodeGrantDataSource implements AuthorizationCodeGrantRepository {

  AuthorizationCodeGrantExecutors executors;

  public AuthorizationCodeGrantDataSource() {
    this.executors = new AuthorizationCodeGrantExecutors();
    ;
  }

  @Override
  public void register(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {

    AuthorizationCodeGrantExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, authorizationCodeGrant);
  }

  @Override
  public AuthorizationCodeGrant find(Tenant tenant, AuthorizationCode authorizationCode) {
    AuthorizationCodeGrantExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> stringMap = executor.selectOne(tenant, authorizationCode);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationCodeGrant();
    }
    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {
    AuthorizationCodeGrantExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, authorizationCodeGrant);
  }
}
