package org.idp.server.control_plane.management.identity.verification;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.basic.exception.UnSupportedException;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigUpdateRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;

public interface IdentityVerificationConfigManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "register",
        new AdminPermissions(Set.of(AdminPermission.IDENTITY_VERIFICATION_CONFIG_CREATE)));
    map.put(
        "findList",
        new AdminPermissions(Set.of(AdminPermission.IDENTITY_VERIFICATION_CONFIG_READ)));
    map.put("get", new AdminPermissions(Set.of(AdminPermission.IDENTITY_VERIFICATION_CONFIG_READ)));
    map.put(
        "update",
        new AdminPermissions(Set.of(AdminPermission.IDENTITY_VERIFICATION_CONFIG_UPDATE)));
    map.put(
        "delete",
        new AdminPermissions(Set.of(AdminPermission.IDENTITY_VERIFICATION_CONFIG_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  IdentityVerificationConfigManagementResponse register(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  IdentityVerificationConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  IdentityVerificationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier userIdentifier,
      RequestAttributes requestAttributes);

  IdentityVerificationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier userIdentifier,
      IdentityVerificationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  IdentityVerificationConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
