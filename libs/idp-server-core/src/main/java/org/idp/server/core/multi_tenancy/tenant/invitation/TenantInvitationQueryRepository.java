package org.idp.server.core.multi_tenancy.tenant.invitation;

import java.util.List;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface TenantInvitationQueryRepository {

  List<TenantInvitation> findList(Tenant tenant, int limit, int offset);

  TenantInvitation find(Tenant tenant, TenantInvitationIdentifier identifier);
}
