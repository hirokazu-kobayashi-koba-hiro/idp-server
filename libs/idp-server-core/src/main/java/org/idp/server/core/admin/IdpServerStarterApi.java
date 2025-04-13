package org.idp.server.core.admin;

import java.util.Map;
import org.idp.server.core.tenant.TenantIdentifier;

public interface IdpServerStarterApi {

  Map<String, Object> initialize(
      TenantIdentifier adminTenantIdentifier, Map<String, Object> request);
}
