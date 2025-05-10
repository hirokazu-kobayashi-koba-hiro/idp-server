package org.idp.server.control_plane;

import org.idp.server.core.multi_tenancy.tenant.ServerDomain;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantType;

public interface AuthorizationServerManagementApi {

  String register(
      TenantIdentifier adminTenantIdentifier,
      TenantType tenantType,
      ServerDomain serverDomain,
      String json);
}
