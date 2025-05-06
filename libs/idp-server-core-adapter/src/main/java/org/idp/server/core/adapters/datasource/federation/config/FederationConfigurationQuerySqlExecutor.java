package org.idp.server.core.adapters.datasource.federation.config;

import java.util.Map;
import org.idp.server.core.federation.FederationType;
import org.idp.server.core.federation.SsoProvider;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface FederationConfigurationQuerySqlExecutor {

  Map<String, String> selectOne(Tenant tenant, FederationType federationType, SsoProvider ssoProvider);
}
