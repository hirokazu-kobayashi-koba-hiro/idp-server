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

package org.idp.server.core.openid.plugin.token;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.token.plugin.AccessTokenCustomClaimsCreator;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class AccessTokenCustomClaimsCreationPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AccessTokenCustomClaimsCreationPluginLoader.class);

  public static List<AccessTokenCustomClaimsCreator> load() {
    List<AccessTokenCustomClaimsCreator> customClaimsCreators = new ArrayList<>();

    List<AccessTokenCustomClaimsCreator> internals =
        loadFromInternalModule(AccessTokenCustomClaimsCreator.class);
    for (AccessTokenCustomClaimsCreator customClaimsCreator : internals) {
      customClaimsCreators.add(customClaimsCreator);
      log.info(
          "Dynamic Registered internal AccessTokenCustomClaimsCreator "
              + customClaimsCreator.getClass().getSimpleName());
    }

    List<AccessTokenCustomClaimsCreator> externals =
        loadFromExternalModule(AccessTokenCustomClaimsCreator.class);
    for (AccessTokenCustomClaimsCreator customClaimsCreator : externals) {
      customClaimsCreators.add(customClaimsCreator);
      log.info(
          "Dynamic Registered external AccessTokenCustomClaimsCreator "
              + customClaimsCreator.getClass().getSimpleName());
    }

    return customClaimsCreators;
  }
}
