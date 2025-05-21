package org.idp.server.core.oidc.verifier.plugin;

import java.util.*;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.verifier.AuthorizationRequestVerifier;
import org.idp.server.platform.log.LoggerWrapper;

public class AuthorizationRequestVerifierLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationRequestVerifierLoader.class);

  public static Map<AuthorizationProfile, AuthorizationRequestVerifier> load() {
    Map<AuthorizationProfile, AuthorizationRequestVerifier> verifierMap = new HashMap<>();

    ServiceLoader<AuthorizationRequestVerifier> serviceLoaders =
        ServiceLoader.load(AuthorizationRequestVerifier.class);
    for (AuthorizationRequestVerifier verifier : serviceLoaders) {
      verifierMap.put(verifier.profile(), verifier);
      log.info("Dynamic Registered  OAuthRequestVerifier " + verifier.getClass().getSimpleName());
    }

    return verifierMap;
  }
}
