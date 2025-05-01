package org.idp.server.core.adapters.datasource.tenant;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.tenant.*;

class ModelConverter {

  private static final JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static Tenant convert(Map<String, String> result) {

    TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getOrDefault("id", ""));
    TenantName tenantName = new TenantName(result.getOrDefault("name", ""));
    TenantType tenantType = TenantType.valueOf(result.getOrDefault("type", ""));
    TenantDomain tenantDomain = new TenantDomain(result.getOrDefault("domain", ""));
    TenantAttributes tenantAttributes = convertAttributes(result.getOrDefault("attributes", ""));

    return new Tenant(tenantIdentifier, tenantName, tenantType, tenantDomain, tenantAttributes);
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
