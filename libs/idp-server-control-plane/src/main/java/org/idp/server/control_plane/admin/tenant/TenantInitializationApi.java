package org.idp.server.control_plane.admin.tenant;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.admin.tenant.io.TenantInitializationRequest;
import org.idp.server.control_plane.admin.tenant.io.TenantInitializationResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface TenantInitializationApi {

  TenantInitializationResponse initialize(
      TenantIdentifier adminTenantIdentifier,
      TenantInitializationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
