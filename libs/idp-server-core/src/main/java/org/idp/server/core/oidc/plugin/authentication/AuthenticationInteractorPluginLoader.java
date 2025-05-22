package org.idp.server.core.oidc.plugin.authentication;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;
import org.idp.server.core.oidc.authentication.AuthenticationInteractors;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationInteractorFactory;
import org.idp.server.platform.log.LoggerWrapper;

public class AuthenticationInteractorPluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationInteractorPluginLoader.class);

  public static AuthenticationInteractors load(AuthenticationDependencyContainer container) {

    Map<AuthenticationInteractionType, AuthenticationInteractor> interactors = new HashMap<>();
    ServiceLoader<AuthenticationInteractorFactory> loader =
        ServiceLoader.load(AuthenticationInteractorFactory.class);

    for (AuthenticationInteractorFactory factory : loader) {
      AuthenticationInteractor interactor = factory.create(container);
      interactors.put(factory.type(), interactor);
      log.info("Dynamic Registered Authentication interactor: " + factory.type().name());
    }

    return new AuthenticationInteractors(interactors);
  }
}
