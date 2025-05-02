package org.idp.server.core.adapters.datasource.federation.session.query;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.federation.SsoSessionIdentifier;
import org.idp.server.core.federation.SsoSessionNotFoundException;
import org.idp.server.core.federation.SsoSessionQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class SsoSessionQueryDataSource implements SsoSessionQueryRepository {

  SsoSessionQuerySqlExecutors executors;
  JsonConverter jsonConverter;

  public SsoSessionQueryDataSource() {
    this.executors = new SsoSessionQuerySqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> T get(Tenant tenant, SsoSessionIdentifier ssoSessionIdentifier, Class<T> clazz) {
    SsoSessionQuerySqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(ssoSessionIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new SsoSessionNotFoundException(
          String.format("federation sso session is not found (%s)", ssoSessionIdentifier.value()));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}
