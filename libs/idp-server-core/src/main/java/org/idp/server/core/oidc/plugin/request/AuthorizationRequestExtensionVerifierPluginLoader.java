/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.plugin.request;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.platform.log.LoggerWrapper;

public class AuthorizationRequestExtensionVerifierPluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationRequestExtensionVerifierPluginLoader.class);

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
