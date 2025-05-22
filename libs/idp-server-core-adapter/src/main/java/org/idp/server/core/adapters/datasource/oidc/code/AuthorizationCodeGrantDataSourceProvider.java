/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.oidc.code;

import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class AuthorizationCodeGrantDataSourceProvider
    implements ApplicationComponentProvider<AuthorizationCodeGrantRepository> {

  @Override
  public Class<AuthorizationCodeGrantRepository> type() {
    return AuthorizationCodeGrantRepository.class;
  }

  @Override
  public AuthorizationCodeGrantRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new AuthorizationCodeGrantDataSource();
  }
}
