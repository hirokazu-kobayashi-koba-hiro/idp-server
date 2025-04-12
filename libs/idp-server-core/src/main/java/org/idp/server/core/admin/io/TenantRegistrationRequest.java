package org.idp.server.core.admin.io;

import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantName;

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
