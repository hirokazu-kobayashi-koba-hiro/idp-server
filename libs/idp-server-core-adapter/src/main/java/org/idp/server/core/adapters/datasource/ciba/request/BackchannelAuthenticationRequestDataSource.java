package org.idp.server.core.adapters.datasource.ciba.request;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestBuilder;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class BackchannelAuthenticationRequestDataSource
    implements BackchannelAuthenticationRequestRepository {

  BackchannelAuthenticationRequestSqlExecutors executors;
  JsonConverter jsonConverter;

  public BackchannelAuthenticationRequestDataSource() {
    this.executors = new BackchannelAuthenticationRequestSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(Tenant tenant, BackchannelAuthenticationRequest request) {
    BackchannelAuthenticationRequestSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(request);
  }

  @Override
  public BackchannelAuthenticationRequest find(
      Tenant tenant, BackchannelAuthenticationRequestIdentifier identifier) {
    BackchannelAuthenticationRequestSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> stringMap = executor.selectOne(identifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new BackchannelAuthenticationRequestBuilder().build();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, BackchannelAuthenticationRequestIdentifier identifier) {
    BackchannelAuthenticationRequestSqlExecutor executor = executors.get(tenant.databaseType());

    executor.delete(identifier);
  }
}
