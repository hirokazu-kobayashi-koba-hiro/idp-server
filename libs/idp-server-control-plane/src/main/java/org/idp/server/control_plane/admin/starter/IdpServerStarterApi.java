package org.idp.server.control_plane.admin.starter;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterRequest;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterResponse;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface IdpServerStarterApi {

  IdpServerStarterResponse initialize(
      TenantIdentifier adminTenantIdentifier,
      IdpServerStarterRequest request,
      RequestAttributes requestAttributes);
}
