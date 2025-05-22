/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.federation.plugin;

import java.util.ServiceLoader;
import org.idp.server.platform.log.LoggerWrapper;

public class FederationDependencyContainerLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(FederationDependencyContainerLoader.class);

  public static FederationDependencyContainer load() {
    FederationDependencyContainer container = new FederationDependencyContainer();
    ServiceLoader<FederationDependencyProvider> loader =
        ServiceLoader.load(FederationDependencyProvider.class);

    for (FederationDependencyProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide());
      log.info("Dynamic Registered federation dependency provider " + provider.type());
    }

    return container;
  }
}
