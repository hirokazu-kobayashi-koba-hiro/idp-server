/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.multi_tenancy.tenant.*;

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
  public Tenant find(TenantIdentifier tenantIdentifier) {
    String key = key(tenantIdentifier);
    Optional<Tenant> optionalTenant = cacheStore.find(key, Tenant.class);

    if (optionalTenant.isPresent()) {
      return optionalTenant.get();
    }

    TenantQuerySqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);

    Map<String, String> result = executor.selectOne(tenantIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new Tenant();
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

  @Override
  public Tenant findAdmin() {
    TenantQuerySqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);

    Map<String, String> result = executor.selectAdmin();

    if (Objects.isNull(result) || result.isEmpty()) {
      return new Tenant();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public List<Tenant> findList(List<TenantIdentifier> tenantIdentifiers) {
    TenantQuerySqlExecutor executor = executors.get(DatabaseType.POSTGRESQL);

    List<Map<String, String>> results = executor.selectList(tenantIdentifiers);

    if (Objects.isNull(results) || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).toList();
  }

  private String key(TenantIdentifier tenantIdentifier) {
    return "tenantId:" + tenantIdentifier.value() + ":" + Tenant.class.getSimpleName();
  }
}
