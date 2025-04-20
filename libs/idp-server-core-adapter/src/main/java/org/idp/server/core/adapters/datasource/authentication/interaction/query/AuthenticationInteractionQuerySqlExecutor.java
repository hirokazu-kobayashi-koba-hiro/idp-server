package org.idp.server.core.adapters.datasource.authentication.interaction.query;

import java.util.Map;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.tenant.Tenant;

public interface AuthenticationInteractionQuerySqlExecutor {
  Map<String, String> selectOne(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type);
}
