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
import java.util.ServiceLoader;
import org.idp.server.core.oidc.id_token.plugin.CustomIndividualClaimsCreator;
import org.idp.server.platform.log.LoggerWrapper;

public class CustomUserClaimsCreationPluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(CustomUserClaimsCreationPluginLoader.class);

  public static List<CustomIndividualClaimsCreator> load() {
    List<CustomIndividualClaimsCreator> customIndividualClaimsCreators = new ArrayList<>();

    ServiceLoader<CustomIndividualClaimsCreator> serviceLoader =
        ServiceLoader.load(CustomIndividualClaimsCreator.class);
    for (CustomIndividualClaimsCreator customIndividualClaimsCreator : serviceLoader) {
      customIndividualClaimsCreators.add(customIndividualClaimsCreator);
      log.info(
          "Dynamic Registered CustomUserClaimsCreator "
              + customIndividualClaimsCreator.getClass().getSimpleName());
    }

    return customIndividualClaimsCreators;
  }
}
