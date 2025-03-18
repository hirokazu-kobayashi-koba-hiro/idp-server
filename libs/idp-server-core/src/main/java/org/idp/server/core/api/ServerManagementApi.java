package org.idp.server.core.api;

import org.idp.server.core.tenant.PublicTenantDomain;

public interface ServerManagementApi {

  String register(PublicTenantDomain publicTenantDomain, String json);
}
