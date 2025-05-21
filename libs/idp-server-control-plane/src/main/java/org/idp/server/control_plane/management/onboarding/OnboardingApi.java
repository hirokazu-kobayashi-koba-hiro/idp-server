package org.idp.server.control_plane.management.onboarding;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.management.onboarding.io.OnboardingResponse;
import org.idp.server.core.identity.User;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;

public interface OnboardingApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "onboard",
        new AdminPermissions(
            Set.of(
                AdminPermission.ORGANIZATION_CREATE,
                AdminPermission.TENANT_CREATE,
                AdminPermission.CLIENT_CREATE)));
    AdminPermissions adminPermissions = map.get(method);

    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }

    return adminPermissions;
  }

  OnboardingResponse onboard(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OnboardingRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
