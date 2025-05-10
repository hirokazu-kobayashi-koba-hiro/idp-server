package org.idp.server.control_plane.starter;

import org.idp.server.control_plane.starter.io.IdpServerStarterRequest;
import org.idp.server.control_plane.starter.io.IdpServerStarterResponse;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface IdpServerStarterApi {

  IdpServerStarterResponse initialize(
      TenantIdentifier adminTenantIdentifier, IdpServerStarterRequest request);
}
