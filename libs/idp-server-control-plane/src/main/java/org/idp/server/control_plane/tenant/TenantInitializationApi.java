package org.idp.server.control_plane.tenant;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.tenant.io.TenantInitializationRequest;
import org.idp.server.control_plane.tenant.io.TenantInitializationResponse;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface TenantInitializationApi {

  TenantInitializationResponse initialize(
      TenantIdentifier adminTenantIdentifier,
      TenantInitializationRequest request,
      RequestAttributes requestAttributes);
}
