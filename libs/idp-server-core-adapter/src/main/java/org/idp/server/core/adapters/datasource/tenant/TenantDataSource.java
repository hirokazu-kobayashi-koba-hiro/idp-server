package org.idp.server.core.adapters.datasource.tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.tenant.*;
import org.idp.server.core.type.oauth.TokenIssuer;

public class TenantDataSource implements TenantRepository {

  @Override
  public void register(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
            INSERT INTO tenant(id, name, type, issuer)
            VALUES (?, ?, ?, ?);
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(tenant.name().value());
    params.add(tenant.type().name());
    params.add(tenant.issuer());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Tenant get(TenantIdentifier tenantIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
            SELECT id, name, type, issuer FROM tenant
            WHERE id = ?
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.value());

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new TenantNotFoundException(
          String.format("Tenant is not found (%s)", tenantIdentifier.value()));
    }
    TenantName tenantName = new TenantName(result.getOrDefault("name", ""));
    TenantType tenantType = TenantType.valueOf(result.getOrDefault("type", ""));
    String issuer = result.getOrDefault("issuer", "");

    return new Tenant(tenantIdentifier, tenantName, tenantType, issuer);
  }

  @Override
  public Tenant getAdmin() {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
            SELECT id, name, type, issuer FROM tenant
            WHERE type = ?
            """;
    List<Object> params = new ArrayList<>();
    params.add("ADMIN");

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new TenantNotFoundException("Admin Tenant is unregistered.");
    }

    TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getOrDefault("identifier", ""));
    TenantName tenantName = new TenantName(result.getOrDefault("name", ""));
    TenantType tenantType = TenantType.valueOf(result.getOrDefault("type", ""));
    String issuer = result.getOrDefault("issuer", "");

    return new Tenant(tenantIdentifier, tenantName, tenantType, issuer);
  }

  @Override
  public void update(Tenant tenant) {}

  @Override
  public void delete(TenantIdentifier tenantIdentifier) {}

  @Override
  public Tenant find(TokenIssuer tokenIssuer) {

    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
            SELECT id, name, type, issuer FROM tenant
            WHERE issuer = ?
            """;
    List<Object> params = new ArrayList<>();
    params.add(tokenIssuer.value());

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new Tenant();
    }
    TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getOrDefault("id", ""));
    TenantName tenantName = new TenantName(result.getOrDefault("name", ""));
    TenantType tenantType = TenantType.valueOf(result.getOrDefault("type", ""));
    String issuer = result.getOrDefault("issuer", "");

    return new Tenant(tenantIdentifier, tenantName, tenantType, issuer);
  }
}
