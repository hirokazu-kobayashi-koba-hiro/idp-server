package org.idp.server.core.api;

import org.idp.server.core.tenant.Tenant;

public interface ServerManagementApi {

  String register(Tenant tenant, String json);
}
