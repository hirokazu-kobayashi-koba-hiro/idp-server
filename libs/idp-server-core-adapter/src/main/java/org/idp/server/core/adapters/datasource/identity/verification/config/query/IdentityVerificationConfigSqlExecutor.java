package org.idp.server.core.adapters.datasource.identity.verification.config.query;

import java.util.Map;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationConfigSqlExecutor {
  Map<String, String> selectOne(Tenant tenant, IdentityVerificationType key);
}
