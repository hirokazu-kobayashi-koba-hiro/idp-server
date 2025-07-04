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

package org.idp.server.core.extension.identity.verification.plugin;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.verification.delegation.request.AdditionalRequestParameterResolver;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class IdentityVerificationRequestAdditionalParameterPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(IdentityVerificationRequestAdditionalParameterPluginLoader.class);

  public static List<AdditionalRequestParameterResolver> load() {
    List<AdditionalRequestParameterResolver> resolvers = new ArrayList<>();

    List<AdditionalRequestParameterResolver> internals =
        loadFromInternalModule(AdditionalRequestParameterResolver.class);
    for (AdditionalRequestParameterResolver resolver : internals) {
      resolvers.add(resolver);
      log.info(
          String.format(
              "Dynamic Registered internal AdditionalRequestParameterResolver %s",
              resolver.getClass().getSimpleName()));
    }

    List<AdditionalRequestParameterResolver> externals =
        loadFromExternalModule(AdditionalRequestParameterResolver.class);
    for (AdditionalRequestParameterResolver resolver : externals) {
      resolvers.add(resolver);
      log.info(
          String.format(
              "Dynamic Registered externals AdditionalRequestParameterResolver %s",
              resolver.getClass().getSimpleName()));
    }

    return resolvers;
  }
}
