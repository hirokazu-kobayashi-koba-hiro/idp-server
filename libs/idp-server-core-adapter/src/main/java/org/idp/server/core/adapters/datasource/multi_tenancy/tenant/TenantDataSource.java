package org.idp.server.core.adapters.datasource.multi_tenancy.tenant;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.*;

public class TenantDataSource implements TenantRepository {

  TenantSqlExecutors executors;
  JsonConverter jsonConverter;
  CacheStore cacheStore;

  public TenantDataSource(CacheStore cacheStore) {
    this.executors = new TenantSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
    this.cacheStore = cacheStore;
  }

  @Override
  public void register(Tenant tenant) {
    TenantSqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);
    executor.insert(tenant);
    String key = key(tenant.identifier());
    cacheStore.put(key, tenant, 300);
  }

  @Override
  public Tenant get(TenantIdentifier tenantIdentifier) {
    String key = key(tenantIdentifier);
    Optional<Tenant> optionalTenant = cacheStore.find(key, Tenant.class);

    if (optionalTenant.isPresent()) {
      return optionalTenant.get();
    }

    TenantSqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);

    Map<String, String> result = executor.selectOne(tenantIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new TenantNotFoundException(
          String.format("Tenant is not found (%s)", tenantIdentifier.value()));
    }

    Tenant convert = ModelConverter.convert(result);

    cacheStore.put(key, convert, 300);

    return convert;
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

  private String key(TenantIdentifier tenantIdentifier) {
    return "tenantId:" + tenantIdentifier.value() + ":" + Tenant.class.getSimpleName();
  }
}
