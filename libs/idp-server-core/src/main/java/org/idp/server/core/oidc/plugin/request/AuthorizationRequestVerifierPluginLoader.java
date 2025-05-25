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

import java.util.*;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.verifier.AuthorizationRequestVerifier;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class AuthorizationRequestVerifierPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationRequestVerifierPluginLoader.class);

  public static Map<AuthorizationProfile, AuthorizationRequestVerifier> load() {
    Map<AuthorizationProfile, AuthorizationRequestVerifier> verifierMap = new HashMap<>();

    List<AuthorizationRequestVerifier> internals =
        loadFromInternalModule(AuthorizationRequestVerifier.class);
    for (AuthorizationRequestVerifier verifier : internals) {
      verifierMap.put(verifier.profile(), verifier);
      log.info(
          "Dynamic Registered internal OAuthRequestVerifier "
              + verifier.getClass().getSimpleName());
    }

    List<AuthorizationRequestVerifier> externals =
        loadFromExternalModule(AuthorizationRequestVerifier.class);
    for (AuthorizationRequestVerifier verifier : externals) {
      verifierMap.put(verifier.profile(), verifier);
      log.info(
          "Dynamic Registered external OAuthRequestVerifier "
              + verifier.getClass().getSimpleName());
    }

    return verifierMap;
  }
}
