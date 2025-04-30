package org.idp.server.core.admin;

import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;

public interface OnboardingApi {

  Map<String, Object> initialize(
      TenantIdentifier adminTenantIdentifier, User operator, Map<String, Object> request);
}
