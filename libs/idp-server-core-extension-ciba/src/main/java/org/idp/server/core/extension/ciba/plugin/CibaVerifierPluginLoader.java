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
import org.idp.server.core.extension.ciba.verifier.CibaVerifier;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class CibaVerifierPluginLoader extends PluginLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(CibaVerifierPluginLoader.class);

  public static Map<CibaProfile, CibaVerifier> load() {
    Map<CibaProfile, CibaVerifier> verifierMap = new HashMap<>();

    List<CibaVerifier> internals = loadFromInternalModule(CibaVerifier.class);
    for (CibaVerifier cibaVerifier : internals) {
      verifierMap.put(cibaVerifier.profile(), cibaVerifier);
      log.info(
          "Dynamic Registered internal CibaVerifier " + cibaVerifier.getClass().getSimpleName());
    }

    List<CibaVerifier> externals = loadFromExternalModule(CibaVerifier.class);
    for (CibaVerifier cibaVerifier : externals) {
      verifierMap.put(cibaVerifier.profile(), cibaVerifier);
      log.info(
          "Dynamic Registered external CibaVerifier " + cibaVerifier.getClass().getSimpleName());
    }

    return verifierMap;
  }
}
