package org.idp.server.control_plane.management.tenant.io;

import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface TenantManagementRequest {

  TenantIdentifier tenantIdentifier();

  Map<String, Object> toMap();

  boolean hasTenantIdentifier();
}
