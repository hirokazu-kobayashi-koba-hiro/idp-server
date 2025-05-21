package org.idp.server.core.adapters.datasource.authentication.interaction.query;

import java.util.Map;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationInteractionQuerySqlExecutor {
  Map<String, String> selectOne(Tenant tenant, AuthorizationIdentifier identifier, String type);
}
