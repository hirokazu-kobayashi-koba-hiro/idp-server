package org.idp.server.core.tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.idp.server.core.basic.datasource.DatabaseType;
import org.idp.server.core.basic.dependency.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.basic.dependency.protcol.DefaultAuthorizationProvider;

public class TenantAttributes {
  Map<String, Object> values;

  public static TenantAttributes createDefaultType() {
    return new TenantAttributes(
        Map.of("authorization_protocol_provider", "idp_server", "database_type", "postgresql"));
  }

  public TenantAttributes() {
    this.values = new HashMap<>();
  }

  public TenantAttributes(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) values.get(key);
    }
    return defaultValue;
  }

  public String getValueAsString(String key) {
    return (String) values.get(key);
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public void forEach(BiConsumer<String, Object> action) {
    values.forEach(action);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public AuthorizationProtocolProvider authorizationProtocolProvider() {
    if (!values.containsKey("authorization_protocol_provider")) {
      return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
    }

    return new AuthorizationProtocolProvider(getValueAsString("authorization_protocol_provider"));
  }

  public DatabaseType databaseType() {
    if (!values.containsKey("database_type")) {
      return DatabaseType.POSTGRESQL;
    }

    return DatabaseType.of(getValueAsString("database_type"));
  }
}
