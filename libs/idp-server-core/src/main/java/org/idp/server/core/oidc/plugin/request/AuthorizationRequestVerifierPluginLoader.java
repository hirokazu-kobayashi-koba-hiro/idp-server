/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.plugin.request;

import java.util.*;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.verifier.AuthorizationRequestVerifier;
import org.idp.server.platform.log.LoggerWrapper;

public class AuthorizationRequestVerifierPluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationRequestVerifierPluginLoader.class);

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
