package org.idp.server.core.adapters.datasource.federation.config.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.federation.FederationConfigurationIdentifier;
import org.idp.server.core.oidc.federation.FederationType;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface FederationConfigurationSqlExecutor {

  Map<String, String> selectOne(
      Tenant tenant, FederationType federationType, SsoProvider ssoProvider);

  Map<String, String> selectOne(Tenant tenant, FederationConfigurationIdentifier identifier);

  List<Map<String, String>> selectList(Tenant tenant, int limit, int offset);
}
