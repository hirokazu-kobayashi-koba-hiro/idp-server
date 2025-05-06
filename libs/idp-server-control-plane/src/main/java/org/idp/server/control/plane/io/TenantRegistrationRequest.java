package org.idp.server.control.plane.io;

import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantName;

public class TenantRegistrationRequest implements JsonReadable {

  String id;
  String name;

  public TenantRegistrationRequest() {}

  public TenantRegistrationRequest(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(id);
  }

  public TenantName tenantName() {
    return new TenantName(name);
  }
}
