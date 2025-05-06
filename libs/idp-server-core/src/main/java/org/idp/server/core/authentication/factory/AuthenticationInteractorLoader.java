package org.idp.server.core.authentication.factory;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.authentication.AuthenticationInteractionType;
import org.idp.server.core.authentication.AuthenticationInteractor;
import org.idp.server.core.authentication.AuthenticationInteractors;

public class AuthenticationInteractorLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(AuthenticationInteractorLoader.class);

  public static AuthenticationInteractors load(AuthenticationDependencyContainer container) {

    Map<AuthenticationInteractionType, AuthenticationInteractor> interactors = new HashMap<>();
    ServiceLoader<AuthenticationInteractorFactory> loader = ServiceLoader.load(AuthenticationInteractorFactory.class);

    for (AuthenticationInteractorFactory factory : loader) {
      AuthenticationInteractor interactor = factory.create(container);
      interactors.put(factory.type(), interactor);
      log.info("Dynamic Registered Authentication interactor: " + factory.type().name());
    }

    return new AuthenticationInteractors(interactors);
  }
}
