package org.idp.server.core.federation.factory;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.core.federation.FederationInteractor;
import org.idp.server.core.federation.FederationInteractors;
import org.idp.server.core.federation.FederationType;

public class FederationInteractorLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(FederationInteractorLoader.class);

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
