package org.idp.server.control_plane.management.tenant.invitation.operation;

import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface TenantInvitationQueryRepository {

  List<TenantInvitation> findList(Tenant tenant, int limit, int offset);

  TenantInvitation find(Tenant tenant, TenantInvitationIdentifier identifier);
}
