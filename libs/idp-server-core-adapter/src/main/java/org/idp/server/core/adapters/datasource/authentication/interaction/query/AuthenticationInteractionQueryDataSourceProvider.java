/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.authentication.interaction.query;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyProvider;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionQueryRepository;

public class AuthenticationInteractionQueryDataSourceProvider
    implements AuthenticationDependencyProvider<AuthenticationInteractionQueryRepository> {

  @Override
  public Class<AuthenticationInteractionQueryRepository> type() {
    return AuthenticationInteractionQueryRepository.class;
  }

  @Override
  public AuthenticationInteractionQueryRepository provide() {
    return new AuthenticationInteractionQueryDataSource();
  }
}
