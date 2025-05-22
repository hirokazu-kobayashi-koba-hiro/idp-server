/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.plugin.authentication;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.federation.FederationInteractor;
import org.idp.server.core.oidc.federation.FederationInteractors;
import org.idp.server.core.oidc.federation.FederationType;
import org.idp.server.core.oidc.federation.plugin.FederationDependencyContainer;
import org.idp.server.core.oidc.federation.plugin.FederationInteractorFactory;
import org.idp.server.platform.log.LoggerWrapper;

public class FederationInteractorPluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(FederationInteractorPluginLoader.class);

  public static FederationInteractors load(FederationDependencyContainer container) {
    Map<FederationType, FederationInteractor> executors = new HashMap<>();
    ServiceLoader<FederationInteractorFactory> ssoExecutorServiceLoaders =
        ServiceLoader.load(FederationInteractorFactory.class);

    for (FederationInteractorFactory federationInteractorFactory : ssoExecutorServiceLoaders) {
      FederationType type = federationInteractorFactory.type();
      FederationInteractor federationInteractor = federationInteractorFactory.create(container);
      executors.put(type, federationInteractor);
      log.info("Dynamic Registered SSO executor " + type.name());
    }

    return new FederationInteractors(executors);
  }
}
