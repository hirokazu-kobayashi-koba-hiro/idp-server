package org.idp.server.core.api;

import org.idp.server.core.tenant.ServerDomain;
import org.idp.server.core.tenant.TenantType;

public interface ServerManagementApi {

  String register(TenantType tenantType, ServerDomain serverDomain, String json);
}
