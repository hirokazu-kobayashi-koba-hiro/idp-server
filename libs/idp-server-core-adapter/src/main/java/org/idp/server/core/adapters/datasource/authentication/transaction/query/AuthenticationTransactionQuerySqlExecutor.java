package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationTransactionQuerySqlExecutor {

  Map<String, String> selectOne(Tenant tenant, AuthorizationIdentifier identifier);

  Map<String, String> selectOneByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier authenticationDeviceIdentifier);
}
