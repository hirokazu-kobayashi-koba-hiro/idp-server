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

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.ciba.verifier.additional.CibaRequestAdditionalVerifier;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class CibaRequestAdditionalVerifierPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(CibaRequestAdditionalVerifierPluginLoader.class);

  public static List<CibaRequestAdditionalVerifier> load() {
    List<CibaRequestAdditionalVerifier> customClaimsCreators = new ArrayList<>();

    List<CibaRequestAdditionalVerifier> internals =
        loadFromInternalModule(CibaRequestAdditionalVerifier.class);
    for (CibaRequestAdditionalVerifier cibaRequestAdditionalVerifier : internals) {
      customClaimsCreators.add(cibaRequestAdditionalVerifier);
      log.info(
          "Dynamic Registered internal CibaRequestAdditionalVerifier "
              + cibaRequestAdditionalVerifier.getClass().getSimpleName());
    }

    List<CibaRequestAdditionalVerifier> externals =
        loadFromExternalModule(CibaRequestAdditionalVerifier.class);
    for (CibaRequestAdditionalVerifier cibaRequestAdditionalVerifier : externals) {
      customClaimsCreators.add(cibaRequestAdditionalVerifier);
      log.info(
          "Dynamic Registered external CibaRequestAdditionalVerifier "
              + cibaRequestAdditionalVerifier.getClass().getSimpleName());
    }

    return customClaimsCreators;
  }
}
