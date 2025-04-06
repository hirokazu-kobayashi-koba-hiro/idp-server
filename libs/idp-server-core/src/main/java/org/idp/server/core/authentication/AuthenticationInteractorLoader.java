package org.idp.server.core.authentication;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;

public class AuthenticationInteractorLoader {

  private static final Logger log =
      Logger.getLogger(AuthenticationInteractorLoader.class.getName());

  public static Map<AuthenticationInteractionType, AuthenticationInteractor> load(
      AuthenticationDependencyContainer container) {

    Map<AuthenticationInteractionType, AuthenticationInteractor> interactors = new HashMap<>();
    ServiceLoader<AuthenticationInteractorFactory> loader =
        ServiceLoader.load(AuthenticationInteractorFactory.class);

    for (AuthenticationInteractorFactory factory : loader) {
      AuthenticationInteractor interactor = factory.create(container);
      interactors.put(factory.type(), interactor);
      log.info("Dynamic Registered MFA interactor: " + factory.type().name());
    }

    return interactors;
  }
}
