package org.idp.server.core.mfa;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;

public class MfaInteractorLoader {

  private static Logger log = Logger.getLogger(MfaInteractorLoader.class.getName());

  public static Map<MfaInteractionType, MfaInteractor> load(MfaDependencyContainer container) {

    Map<MfaInteractionType, MfaInteractor> interactors = new HashMap<>();
    ServiceLoader<MfaInteractorFactory> loader = ServiceLoader.load(MfaInteractorFactory.class);

    for (MfaInteractorFactory factory : loader) {
      MfaInteractor interactor = factory.create(container);
      interactors.put(factory.type(), interactor);
      log.info("Dynamic Registered MFA interactor: " + factory.type().name());
    }

    return interactors;
  }
}
