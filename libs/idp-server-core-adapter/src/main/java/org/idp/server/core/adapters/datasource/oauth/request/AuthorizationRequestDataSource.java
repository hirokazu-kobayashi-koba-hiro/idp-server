package org.idp.server.core.adapters.datasource.oauth.request;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oauth.exception.OAuthException;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.tenant.Tenant;

public class AuthorizationRequestDataSource implements AuthorizationRequestRepository {

  AuthorizationRequestSqlExecutors executors;

  public AuthorizationRequestDataSource() {
    this.executors = new AuthorizationRequestSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, AuthorizationRequest authorizationRequest) {

    AuthorizationRequestSqlExecutor executor = executors.get(tenant.dialect());
    executor.insert(authorizationRequest);
  }

  @Override
  public AuthorizationRequest get(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    AuthorizationRequestSqlExecutor executor = executors.get(tenant.dialect());
    Map<String, String> stringMap = executor.selectOne(authorizationRequestIdentifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new OAuthException(
          "invalid_request",
          String.format("not found oauth request (%s)", authorizationRequestIdentifier.value()));
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public AuthorizationRequest find(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    AuthorizationRequestSqlExecutor executor = executors.get(tenant.dialect());
    Map<String, String> stringMap = executor.selectOne(authorizationRequestIdentifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationRequest();
    }

    return ModelConverter.convert(stringMap);
  }
}
