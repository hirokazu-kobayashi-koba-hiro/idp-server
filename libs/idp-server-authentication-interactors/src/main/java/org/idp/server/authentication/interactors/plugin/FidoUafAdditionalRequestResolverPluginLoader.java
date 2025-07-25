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

package org.idp.server.authentication.interactors.plugin;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolver;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolvers;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class FidoUafAdditionalRequestResolverPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(FidoUafAdditionalRequestResolverPluginLoader.class);

  public static FidoUafAdditionalRequestResolvers load() {
    List<FidoUafAdditionalRequestResolver> resolvers = new ArrayList<>();

    List<FidoUafAdditionalRequestResolver> loadedInternalResolvers =
        loadFromInternalModule(FidoUafAdditionalRequestResolver.class);

    for (FidoUafAdditionalRequestResolver resolver : loadedInternalResolvers) {
      resolvers.add(resolver);
      log.info(
          String.format(
              "Dynamic Registered internal FidoUafAdditionalRequestResolverPluginLoader %s",
              resolver.getClass().getName()));
    }

    List<FidoUafAdditionalRequestResolver> resolvedExternalModule =
        loadFromExternalModule(FidoUafAdditionalRequestResolver.class);

    for (FidoUafAdditionalRequestResolver externalResolver : resolvedExternalModule) {
      resolvers.add(externalResolver);
      log.info(
          String.format(
              "Dynamic Registered external FidoUafAdditionalRequestResolverPluginLoader %s",
              externalResolver.getClass().getName()));
    }

    return new FidoUafAdditionalRequestResolvers(resolvers);
  }
}
