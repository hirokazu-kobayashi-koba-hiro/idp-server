/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.authentication.config.query;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyProvider;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;

public class AuthenticationConfigurationDataSourceProvider
    implements AuthenticationDependencyProvider<AuthenticationConfigurationQueryRepository> {

  @Override
  public Class<AuthenticationConfigurationQueryRepository> type() {
    return AuthenticationConfigurationQueryRepository.class;
  }

  @Override
  public AuthenticationConfigurationQueryRepository provide() {
    return new AuthenticationConfigurationQueryDataSource();
  }
}
