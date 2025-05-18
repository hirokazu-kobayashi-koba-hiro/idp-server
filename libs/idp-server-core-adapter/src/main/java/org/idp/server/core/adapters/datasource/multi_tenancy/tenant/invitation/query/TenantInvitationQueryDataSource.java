package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitation;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitationIdentifier;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitationQueryRepository;

public class TenantInvitationQueryDataSource implements TenantInvitationQueryRepository {

  TenantInvitationSqlExecutors executors;

  public TenantInvitationQueryDataSource() {
    this.executors = new TenantInvitationSqlExecutors();
  }

  @Override
  public List<TenantInvitation> findList(Tenant tenant, int limit, int offset) {
    TenantInvitationSqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> results = executor.selectList(tenant, limit, offset);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConvertor::convert).toList();
  }

  @Override
  public TenantInvitation find(Tenant tenant, TenantInvitationIdentifier identifier) {
    TenantInvitationSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      return new TenantInvitation();
    }

    return ModelConvertor.convert(result);
  }
}
