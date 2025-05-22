/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.oidc.request;

import java.util.Map;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthorizationRequestSqlExecutor {

  void insert(Tenant tenant, AuthorizationRequest authorizationRequest);

  Map<String, String> selectOne(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);

  void delete(Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
