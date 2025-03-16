package org.idp.server.core.function;

import org.idp.server.core.tenant.Tenant;

public interface ServerManagementFunction {

  String register(Tenant tenant, String json);
}
