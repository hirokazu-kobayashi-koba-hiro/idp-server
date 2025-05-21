package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.multi_tenancy.tenant.*;

class ModelConverter {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static Tenant convert(Map<String, String> result) {

    TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getOrDefault("id", ""));
    TenantName tenantName = new TenantName(result.getOrDefault("name", ""));
    TenantType tenantType = TenantType.valueOf(result.getOrDefault("type", ""));
    TenantDomain tenantDomain = new TenantDomain(result.getOrDefault("domain", ""));
    AuthorizationProvider authorizationProvider =
        new AuthorizationProvider(result.getOrDefault("authorization_provider", ""));
    DatabaseType databaseType = DatabaseType.of(result.getOrDefault("database_type", ""));
    TenantAttributes tenantAttributes = convertAttributes(result.getOrDefault("attributes", ""));

    return new Tenant(
        tenantIdentifier,
        tenantName,
        tenantType,
        tenantDomain,
        authorizationProvider,
        databaseType,
        tenantAttributes);
  }

  private static TenantAttributes convertAttributes(String value) {
    if (value == null || value.isEmpty()) {
      return new TenantAttributes();
    }
    try {

      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(value);
      Map<String, Object> attributesMap = jsonNodeWrapper.toMap();
      return new TenantAttributes(attributesMap);
    } catch (Exception exception) {
      return new TenantAttributes();
    }
  }
}
