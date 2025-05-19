package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitationIdentifier;

public interface TenantInvitationSqlExecutor {
  Map<String, String> selectOne(Tenant tenant, TenantInvitationIdentifier identifier);

  List<Map<String, String>> selectList(Tenant tenant, int limit, int offset);
}
