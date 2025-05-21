package org.idp.server.control_plane.admin.starter;

import org.idp.server.control_plane.admin.starter.io.IdpServerStarterRequest;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;

public interface IdpServerStarterApi {

  IdpServerStarterResponse initialize(
      TenantIdentifier adminTenantIdentifier,
      IdpServerStarterRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
