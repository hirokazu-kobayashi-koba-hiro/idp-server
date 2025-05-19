package org.idp.server.control_plane.management.tenant.invitation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.basic.exception.UnSupportedException;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementRequest;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementResponse;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitationIdentifier;
import org.idp.server.core.token.OAuthToken;

public interface TenantInvitationManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(AdminPermission.TENANT_INVITATION_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(AdminPermission.TENANT_INVITATION_READ)));
    map.put("get", new AdminPermissions(Set.of(AdminPermission.TENANT_INVITATION_READ)));
    map.put("delete", new AdminPermissions(Set.of(AdminPermission.TENANT_INVITATION_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  TenantInvitationManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantInvitationManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  TenantInvitationManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  TenantInvitationManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantInvitationIdentifier identifier,
      RequestAttributes requestAttributes);

  TenantInvitationManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantInvitationIdentifier identifier,
      TenantInvitationManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  TenantInvitationManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantInvitationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
