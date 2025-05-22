/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.ciba.grant;

import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class CibaGrantDataSourceProvider
    implements ApplicationComponentProvider<CibaGrantRepository> {

  @Override
  public Class<CibaGrantRepository> type() {
    return CibaGrantRepository.class;
  }

  @Override
  public CibaGrantRepository provide(ApplicationComponentDependencyContainer container) {
    return new CibaGrantDataSource();
  }
}
