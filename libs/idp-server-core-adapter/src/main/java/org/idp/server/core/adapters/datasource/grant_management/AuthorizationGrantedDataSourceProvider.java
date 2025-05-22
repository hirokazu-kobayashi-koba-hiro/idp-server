/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.grant_management;

import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class AuthorizationGrantedDataSourceProvider
    implements ApplicationComponentProvider<AuthorizationGrantedRepository> {

  @Override
  public Class<AuthorizationGrantedRepository> type() {
    return AuthorizationGrantedRepository.class;
  }

  @Override
  public AuthorizationGrantedRepository provide(ApplicationComponentDependencyContainer container) {
    return new AuthorizationGrantedDataSource();
  }
}
