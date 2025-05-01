package org.idp.server.core.adapters.datasource.tenant;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.tenant.*;

public class TenantDataSource implements TenantRepository {

  TenantSqlExecutors executors;
  JsonConverter jsonConverter;

  public TenantDataSource() {
    this.executors = new TenantSqlExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(Tenant tenant) {
    TenantSqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);
    executor.insert(tenant);
  }

  @Override
  public Tenant get(TenantIdentifier tenantIdentifier) {
    TenantSqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);

    Map<String, String> result = executor.selectOne(tenantIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new TenantNotFoundException(
          String.format("Tenant is not found (%s)", tenantIdentifier.value()));
    }

    return ModelConverter.convert(result);
  }

  @Override
  public Tenant getAdmin() {
    TenantSqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);

    Map<String, String> result = executor.selectAdmin();

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new TenantNotFoundException("Admin Tenant is unregistered.");
    }

    return ModelConverter.convert(result);
  }

  @Override
  public void update(Tenant tenant) {}

  @Override
  public void delete(TenantIdentifier tenantIdentifier) {}
}
