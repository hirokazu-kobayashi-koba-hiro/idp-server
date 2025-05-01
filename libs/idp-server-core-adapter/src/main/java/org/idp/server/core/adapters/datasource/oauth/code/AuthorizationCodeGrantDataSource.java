package org.idp.server.core.adapters.datasource.oauth.code;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.basic.type.oauth.AuthorizationCode;

public class AuthorizationCodeGrantDataSource implements AuthorizationCodeGrantRepository {

  AuthorizationCodeGrantExecutors executors;
  JsonConverter jsonConverter;

  public AuthorizationCodeGrantDataSource() {
    this.executors = new AuthorizationCodeGrantExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {

    AuthorizationCodeGrantExecutor executor = executors.get(tenant.databaseType());
    executor.insert(authorizationCodeGrant);
  }

  @Override
  public AuthorizationCodeGrant find(Tenant tenant, AuthorizationCode authorizationCode) {
    AuthorizationCodeGrantExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> stringMap = executor.selectOne(authorizationCode);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationCodeGrant();
    }
    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {
    AuthorizationCodeGrantExecutor executor = executors.get(tenant.databaseType());
    executor.delete(authorizationCodeGrant);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
