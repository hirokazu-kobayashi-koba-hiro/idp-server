package org.idp.server.core.adapters.datasource.identity.verification.config.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationConfigSqlExecutor {
  Map<String, String> selectOne(Tenant tenant, IdentityVerificationType key);

  Map<String, String> selectOne(
      Tenant tenant, IdentityVerificationConfigurationIdentifier identifier);

  List<Map<String, String>> selectList(Tenant tenant, int limit, int offset);
}
