package org.idp.server.core.multi_tenancy.tenant.invitation;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface TenantInvitationMetaDataApi {

  TenantInvitationMetaDataResponse get(
      TenantIdentifier tenantIdentifier,
      TenantInvitationIdentifier identifier,
      RequestAttributes requestAttributes);
}
