package org.idp.server.core.adapters.datasource.identity.verification.config;

import java.util.Map;
import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationConfigSqlExecutor {
  Map<String, String> selectOne(Tenant tenant, String key);
}
