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

package org.idp.server.core.openid.token.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.token.verifier.AuthorizationCodeGrantVerifierInterface;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class AuthorizationCodeGrantVerifierPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationCodeGrantVerifierPluginLoader.class);

  public static Map<AuthorizationProfile, AuthorizationCodeGrantVerifierInterface> load() {
    Map<AuthorizationProfile, AuthorizationCodeGrantVerifierInterface> verifiers = new HashMap<>();

    List<AuthorizationCodeGrantVerifierInterface> internals =
        loadFromInternalModule(AuthorizationCodeGrantVerifierInterface.class);
    for (AuthorizationCodeGrantVerifierInterface verifier : internals) {
      verifiers.put(verifier.profile(), verifier);
      log.info(
          String.format(
              "Dynamic Registered internal AuthorizationCodeGrantVerifierInterface %s",
              verifier.getClass().getSimpleName()));
    }

    List<AuthorizationCodeGrantVerifierInterface> externals =
        loadFromExternalModule(AuthorizationCodeGrantVerifierInterface.class);
    for (AuthorizationCodeGrantVerifierInterface verifier : externals) {
      verifiers.put(verifier.profile(), verifier);
      log.info(
          String.format(
              "Dynamic Registered externals AuthorizationCodeGrantVerifierInterface %s",
              verifier.getClass().getSimpleName()));
    }

    return verifiers;
  }
}
