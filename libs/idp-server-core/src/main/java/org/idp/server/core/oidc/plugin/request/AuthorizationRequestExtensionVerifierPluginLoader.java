/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.oidc.plugin.request;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oidc.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class AuthorizationRequestExtensionVerifierPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationRequestExtensionVerifierPluginLoader.class);

  public static List<AuthorizationRequestExtensionVerifier> load() {
    List<AuthorizationRequestExtensionVerifier> extensionVerifiers = new ArrayList<>();

    List<AuthorizationRequestExtensionVerifier> internals =
        loadFromInternalModule(AuthorizationRequestExtensionVerifier.class);
    for (AuthorizationRequestExtensionVerifier authorizationRequestExtensionVerifier : internals) {
      extensionVerifiers.add(authorizationRequestExtensionVerifier);
      log.info(
          "Dynamic Registered internal AuthorizationRequestExtensionVerifier "
              + authorizationRequestExtensionVerifier.getClass().getSimpleName());
    }

    List<AuthorizationRequestExtensionVerifier> externals =
        loadFromExternalModule(AuthorizationRequestExtensionVerifier.class);
    for (AuthorizationRequestExtensionVerifier authorizationRequestExtensionVerifier : externals) {
      extensionVerifiers.add(authorizationRequestExtensionVerifier);
      log.info(
          "Dynamic Registered external AuthorizationRequestExtensionVerifier "
              + authorizationRequestExtensionVerifier.getClass().getSimpleName());
    }

    return extensionVerifiers;
  }
}
