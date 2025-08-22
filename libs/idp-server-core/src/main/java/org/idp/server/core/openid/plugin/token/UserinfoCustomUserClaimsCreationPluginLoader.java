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
import org.idp.server.core.openid.userinfo.plugin.UserinfoCustomIndividualClaimsCreator;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class UserinfoCustomUserClaimsCreationPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(UserinfoCustomUserClaimsCreationPluginLoader.class);

  public static List<UserinfoCustomIndividualClaimsCreator> load() {
    List<UserinfoCustomIndividualClaimsCreator> customIndividualClaimsCreators = new ArrayList<>();

    List<UserinfoCustomIndividualClaimsCreator> internals =
        loadFromInternalModule(UserinfoCustomIndividualClaimsCreator.class);
    for (UserinfoCustomIndividualClaimsCreator customIndividualClaimsCreator : internals) {
      customIndividualClaimsCreators.add(customIndividualClaimsCreator);
      log.info(
          "Dynamic Registered internal UserinfoCustomIndividualClaimsCreator "
              + customIndividualClaimsCreator.getClass().getSimpleName());
    }

    List<UserinfoCustomIndividualClaimsCreator> externals =
        loadFromExternalModule(UserinfoCustomIndividualClaimsCreator.class);
    for (UserinfoCustomIndividualClaimsCreator customIndividualClaimsCreator : externals) {
      customIndividualClaimsCreators.add(customIndividualClaimsCreator);
      log.info(
          "Dynamic Registered external UserinfoCustomIndividualClaimsCreator "
              + customIndividualClaimsCreator.getClass().getSimpleName());
    }

    return customIndividualClaimsCreators;
  }
}
