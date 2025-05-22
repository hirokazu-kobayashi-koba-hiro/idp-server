/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.ciba.request;

import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class BackchannelAuthenticationDataSourceProvider
    implements ApplicationComponentProvider<BackchannelAuthenticationRequestRepository> {

  @Override
  public Class<BackchannelAuthenticationRequestRepository> type() {
    return BackchannelAuthenticationRequestRepository.class;
  }

  @Override
  public BackchannelAuthenticationRequestRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new BackchannelAuthenticationRequestDataSource();
  }
}
