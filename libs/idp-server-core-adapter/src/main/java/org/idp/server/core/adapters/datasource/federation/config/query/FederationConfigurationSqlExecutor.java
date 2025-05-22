/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
