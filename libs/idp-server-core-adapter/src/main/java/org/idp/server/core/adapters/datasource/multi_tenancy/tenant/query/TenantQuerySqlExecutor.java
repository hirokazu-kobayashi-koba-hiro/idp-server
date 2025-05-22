/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface TenantQuerySqlExecutor {

  Map<String, String> selectOne(TenantIdentifier tenantIdentifier);

  Map<String, String> selectAdmin();

  List<Map<String, String>> selectList(List<TenantIdentifier> tenantIdentifiers);
}
