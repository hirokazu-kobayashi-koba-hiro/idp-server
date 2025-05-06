package org.idp.server.core.federation;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.basic.log.LoggerWrapper;

public class FederationInteractorLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(FederationInteractorLoader.class);

  public static FederationInteractors load(FederationDependencyContainer container) {
    Map<FederationType, FederationInteractor> executors = new HashMap<>();
    ServiceLoader<FederationInteractorFactory> ssoExecutorServiceLoaders = ServiceLoader.load(FederationInteractorFactory.class);

    for (FederationInteractorFactory federationInteractorFactory : ssoExecutorServiceLoaders) {
      FederationType type = federationInteractorFactory.type();
      FederationInteractor federationInteractor = federationInteractorFactory.create(container);
      executors.put(type, federationInteractor);
      log.info("Dynamic Registered SSO executor " + type.name());
    }

    return new FederationInteractors(executors);
  }
}
