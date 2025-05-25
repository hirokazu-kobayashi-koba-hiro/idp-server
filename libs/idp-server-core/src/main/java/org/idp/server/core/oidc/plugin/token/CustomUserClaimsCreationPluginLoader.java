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

package org.idp.server.core.oidc.plugin.token;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oidc.id_token.plugin.CustomIndividualClaimsCreator;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class CustomUserClaimsCreationPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(CustomUserClaimsCreationPluginLoader.class);

  public static List<CustomIndividualClaimsCreator> load() {
    List<CustomIndividualClaimsCreator> customIndividualClaimsCreators = new ArrayList<>();

    List<CustomIndividualClaimsCreator> internals =
        loadFromInternalModule(CustomIndividualClaimsCreator.class);
    for (CustomIndividualClaimsCreator customIndividualClaimsCreator : internals) {
      customIndividualClaimsCreators.add(customIndividualClaimsCreator);
      log.info(
          "Dynamic Registered internal CustomUserClaimsCreator "
              + customIndividualClaimsCreator.getClass().getSimpleName());
    }

    List<CustomIndividualClaimsCreator> externals =
        loadFromExternalModule(CustomIndividualClaimsCreator.class);
    for (CustomIndividualClaimsCreator customIndividualClaimsCreator : externals) {
      customIndividualClaimsCreators.add(customIndividualClaimsCreator);
      log.info(
          "Dynamic Registered external CustomUserClaimsCreator "
              + customIndividualClaimsCreator.getClass().getSimpleName());
    }

    return customIndividualClaimsCreators;
  }
}
