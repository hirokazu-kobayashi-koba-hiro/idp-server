package org.idp.server.core.admin;

import org.idp.server.core.tenant.ServerDomain;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantType;

public interface ServerManagementApi {

  String register(
      TenantIdentifier adminTenantIdentifier,
      TenantType tenantType,
      ServerDomain serverDomain,
      String json);
}
