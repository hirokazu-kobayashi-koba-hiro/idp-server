package org.idp.server.core.adapters.datasource.tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.tenant.*;

public class TenantDataSource implements TenantRepository {

  TenantSqlExecutors executors;

  public TenantDataSource() {
    this.executors = new TenantSqlExecutors();
  }

  @Override
  public void register(Tenant tenant) {
    TenantSqlExecutor executor = executors.get(Dialect.POSTGRESQL);
    executor.insert(tenant);
  }

  @Override
  public Tenant get(TenantIdentifier tenantIdentifier) {
    TenantSqlExecutor executor = executors.get(Dialect.POSTGRESQL);

    Map<String, String> result = executor.selectOne(tenantIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new TenantNotFoundException(
          String.format("Tenant is not found (%s)", tenantIdentifier.value()));
    }
    TenantName tenantName = new TenantName(result.getOrDefault("name", ""));
    TenantType tenantType = TenantType.valueOf(result.getOrDefault("type", ""));
    TenantDomain tenantDomain = new TenantDomain(result.getOrDefault("domain", ""));

    return new Tenant(tenantIdentifier, tenantName, tenantType, tenantDomain);
  }

  @Override
  public Tenant getAdmin() {
    TenantSqlExecutor executor = executors.get(Dialect.POSTGRESQL);

    Map<String, String> result = executor.selectAdmin();

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new TenantNotFoundException("Admin Tenant is unregistered.");
    }

    TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getOrDefault("id", ""));
    TenantName tenantName = new TenantName(result.getOrDefault("name", ""));
    TenantType tenantType = TenantType.valueOf(result.getOrDefault("type", ""));
    TenantDomain tenantDomain = new TenantDomain(result.getOrDefault("domain", ""));

    return new Tenant(tenantIdentifier, tenantName, tenantType, tenantDomain);
  }

  @Override
  public void update(Tenant tenant) {}

  @Override
  public void delete(TenantIdentifier tenantIdentifier) {}
}
