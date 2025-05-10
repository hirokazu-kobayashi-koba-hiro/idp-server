package org.idp.server.control_plane;

import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface OnboardingApi {

  Map<String, Object> initialize(
      TenantIdentifier adminTenantIdentifier, User operator, Map<String, Object> request);
}
