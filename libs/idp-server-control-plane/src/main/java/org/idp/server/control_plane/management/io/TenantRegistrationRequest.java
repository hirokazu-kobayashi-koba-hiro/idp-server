package org.idp.server.control_plane.management.io;

import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.dependency.protocol.AuthorizationProvider;
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

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(id);
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
