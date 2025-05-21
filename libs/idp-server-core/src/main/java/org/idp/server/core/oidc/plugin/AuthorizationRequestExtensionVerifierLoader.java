package org.idp.server.core.oidc.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.platform.log.LoggerWrapper;

public class AuthorizationRequestExtensionVerifierLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationRequestExtensionVerifierLoader.class);

  public static List<AuthorizationRequestExtensionVerifier> load() {
    List<AuthorizationRequestExtensionVerifier> extensionVerifiers = new ArrayList<>();

    ServiceLoader<AuthorizationRequestExtensionVerifier> serviceLoaders =
        ServiceLoader.load(AuthorizationRequestExtensionVerifier.class);
    for (AuthorizationRequestExtensionVerifier authorizationRequestExtensionVerifier :
        serviceLoaders) {
      extensionVerifiers.add(authorizationRequestExtensionVerifier);
      log.info(
          "Dynamic Registered  AuthorizationRequestExtensionVerifier "
              + authorizationRequestExtensionVerifier.getClass().getSimpleName());
    }

    return extensionVerifiers;
  }
}
