package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.*;

public class TenantQueryDataSource implements TenantQueryRepository {

  TenantQuerySqlExecutors executors;
  JsonConverter jsonConverter;
  CacheStore cacheStore;

  public TenantQueryDataSource(CacheStore cacheStore) {
    this.executors = new TenantQuerySqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
    this.cacheStore = cacheStore;
  }

  @Override
  public Tenant get(TenantIdentifier tenantIdentifier) {
    String key = key(tenantIdentifier);
    Optional<Tenant> optionalTenant = cacheStore.find(key, Tenant.class);

    if (optionalTenant.isPresent()) {
      return optionalTenant.get();
    }

    TenantQuerySqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);

    Map<String, String> result = executor.selectOne(tenantIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new TenantNotFoundException(
          String.format("Tenant is not found (%s)", tenantIdentifier.value()));
    }

    Tenant convert = ModelConverter.convert(result);

    cacheStore.put(key, convert);

    return convert;
  }

  @Override
  public Tenant getAdmin() {
    TenantQuerySqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);

    Map<String, String> result = executor.selectAdmin();

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new TenantNotFoundException("Admin Tenant is unregistered.");
    }

    return ModelConverter.convert(result);
  }

  private String key(TenantIdentifier tenantIdentifier) {
    return "tenantId:" + tenantIdentifier.value() + ":" + Tenant.class.getSimpleName();
  }
}
