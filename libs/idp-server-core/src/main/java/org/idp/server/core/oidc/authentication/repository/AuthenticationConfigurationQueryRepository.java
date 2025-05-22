/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.authentication.repository;

import java.util.List;
import org.idp.server.core.oidc.authentication.AuthenticationConfiguration;
import org.idp.server.core.oidc.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationConfigurationQueryRepository {
  <T> T get(Tenant tenant, String key, Class<T> clazz);

  AuthenticationConfiguration find(Tenant tenant, AuthenticationConfigurationIdentifier identifier);

  List<AuthenticationConfiguration> findList(Tenant tenant, int limit, int offset);
}
