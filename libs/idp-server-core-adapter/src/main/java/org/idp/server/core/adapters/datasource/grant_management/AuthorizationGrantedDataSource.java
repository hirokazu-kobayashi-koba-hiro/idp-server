package org.idp.server.core.adapters.datasource.grant_management;

import java.util.*;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.grant_management.AuthorizationGranted;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthorizationGrantedDataSource implements AuthorizationGrantedRepository {

  JsonConverter jsonConverter;
  AuthorizationGrantedSqlExecutors executors;

  public AuthorizationGrantedDataSource() {
    this.executors = new AuthorizationGrantedSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(Tenant tenant, AuthorizationGranted authorizationGranted) {
    AuthorizationGrantedSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(authorizationGranted);
  }

  @Override
  public AuthorizationGranted find(Tenant tenant, RequestedClientId requestedClientId, User user) {
    AuthorizationGrantedSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant.identifier(), requestedClientId, user);

    if (result == null || result.isEmpty()) {
      return new AuthorizationGranted();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public void update(Tenant tenant, AuthorizationGranted authorizationGranted) {
    AuthorizationGrantedSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(authorizationGranted);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
