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

package org.idp.server.core.oidc.token.plugin;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oidc.token.verifier.AuthorizationCodeGrantExtensionVerifierInterface;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class AuthorizationCodeGrantExtensionVerifierPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationCodeGrantExtensionVerifierPluginLoader.class);

  public static List<AuthorizationCodeGrantExtensionVerifierInterface> load() {
    List<AuthorizationCodeGrantExtensionVerifierInterface> verifiers = new ArrayList<>();

    List<AuthorizationCodeGrantExtensionVerifierInterface> internals =
        loadFromInternalModule(AuthorizationCodeGrantExtensionVerifierInterface.class);
    for (AuthorizationCodeGrantExtensionVerifierInterface verifier : internals) {
      verifiers.add(verifier);
      log.info(
          String.format(
              "Dynamic Registered internal AuthorizationCodeGrantExtensionVerifierInterface %s",
              verifier.getClass().getSimpleName()));
    }

    List<AuthorizationCodeGrantExtensionVerifierInterface> externals =
        loadFromExternalModule(AuthorizationCodeGrantExtensionVerifierInterface.class);
    for (AuthorizationCodeGrantExtensionVerifierInterface verifier : externals) {
      verifiers.add(verifier);
      log.info(
          String.format(
              "Dynamic Registered externals AuthorizationCodeGrantExtensionVerifierInterface %s",
              verifier.getClass().getSimpleName()));
    }

    return verifiers;
  }
}
