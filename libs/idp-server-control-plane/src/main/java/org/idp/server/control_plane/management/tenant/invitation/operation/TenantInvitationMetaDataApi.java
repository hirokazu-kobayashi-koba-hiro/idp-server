package org.idp.server.control_plane.management.tenant.invitation.operation;

import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;

public interface TenantInvitationMetaDataApi {

  TenantInvitationMetaDataResponse get(
      TenantIdentifier tenantIdentifier,
      TenantInvitationIdentifier identifier,
      RequestAttributes requestAttributes);
}
