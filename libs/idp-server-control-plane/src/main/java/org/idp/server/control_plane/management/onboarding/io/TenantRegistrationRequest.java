package org.idp.server.control_plane.management.onboarding.io;

import java.util.UUID;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.multi_tenancy.tenant.TenantDomain;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantName;

public class TenantRegistrationRequest implements JsonReadable {

  String id;
  String name;
  String domain;
  String authorizationProvider;
  String databaseType;

  public TenantRegistrationRequest() {}

  public boolean hasId() {
    return id != null && !id.isEmpty();
  }

  public TenantIdentifier tenantIdentifier() {
    if (hasId()) {
      return new TenantIdentifier(id);
    }
    return new TenantIdentifier(UUID.randomUUID().toString());
  }

  public TenantName tenantName() {
    return new TenantName(name);
  }

  public TenantDomain tenantDomain() {
    return new TenantDomain(domain);
  }

  public AuthorizationProvider authorizationProvider() {
    return new AuthorizationProvider(authorizationProvider);
  }

  public DatabaseType databaseType() {
    return DatabaseType.of(databaseType);
  }
}
