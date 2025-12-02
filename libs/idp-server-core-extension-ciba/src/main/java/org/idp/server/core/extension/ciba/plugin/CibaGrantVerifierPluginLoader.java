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

package org.idp.server.core.extension.ciba.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.token.CibaGrantVerifierInterface;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

/**
 * CibaGrantVerifierPluginLoader
 *
 * <p>Loads {@link CibaGrantVerifierInterface} implementations using the ServiceLoader mechanism.
 * This enables profile-specific CIBA grant verification (e.g., standard CIBA, FAPI-CIBA) to be
 * dynamically registered.
 *
 * @see CibaGrantVerifierInterface
 * @see org.idp.server.platform.plugin.PluginLoader
 */
public class CibaGrantVerifierPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(CibaGrantVerifierPluginLoader.class);

  /**
   * Loads all available CIBA grant verifier implementations.
   *
   * <p>Discovers and loads verifiers from both internal modules (classpath) and external modules
   * (extensions). Each verifier is mapped by its {@link CibaProfile}.
   *
   * @return a map of {@link CibaProfile} to {@link CibaGrantVerifierInterface} implementations
   */
  public static Map<CibaProfile, CibaGrantVerifierInterface> load() {
    Map<CibaProfile, CibaGrantVerifierInterface> verifiers = new HashMap<>();

    List<CibaGrantVerifierInterface> internals =
        loadFromInternalModule(CibaGrantVerifierInterface.class);
    for (CibaGrantVerifierInterface verifier : internals) {
      verifiers.put(verifier.profile(), verifier);
      log.info(
          String.format(
              "Dynamic Registered internal CibaGrantVerifierInterface %s",
              verifier.getClass().getSimpleName()));
    }

    List<CibaGrantVerifierInterface> externals =
        loadFromExternalModule(CibaGrantVerifierInterface.class);
    for (CibaGrantVerifierInterface verifier : externals) {
      verifiers.put(verifier.profile(), verifier);
      log.info(
          String.format(
              "Dynamic Registered externals CibaGrantVerifierInterface %s",
              verifier.getClass().getSimpleName()));
    }

    return verifiers;
  }
}
