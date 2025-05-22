/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.onboarding;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.management.onboarding.io.OnboardingResponse;
import org.idp.server.core.oidc.identity.User;
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
