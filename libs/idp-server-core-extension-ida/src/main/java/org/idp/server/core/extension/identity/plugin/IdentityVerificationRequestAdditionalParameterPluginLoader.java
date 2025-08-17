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

package org.idp.server.core.extension.identity.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalRequestParameterResolver;
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalRequestParameterResolverFactory;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class IdentityVerificationRequestAdditionalParameterPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(IdentityVerificationRequestAdditionalParameterPluginLoader.class);

  public static Map<String, AdditionalRequestParameterResolver> load(
      ApplicationComponentContainer container) {
    Map<String, AdditionalRequestParameterResolver> resolvers = new HashMap<>();

    List<AdditionalRequestParameterResolverFactory> internals =
        loadFromInternalModule(AdditionalRequestParameterResolverFactory.class);
    for (AdditionalRequestParameterResolverFactory resolverFactory : internals) {
      AdditionalRequestParameterResolver resolver = resolverFactory.create(container);
      resolvers.put(resolver.type(), resolver);
      log.info(
          String.format(
              "Dynamic Registered internal AdditionalRequestParameterResolver %s",
              resolver.getClass().getSimpleName()));
    }

    List<AdditionalRequestParameterResolverFactory> externals =
        loadFromInternalModule(AdditionalRequestParameterResolverFactory.class);
    for (AdditionalRequestParameterResolverFactory resolverFactory : externals) {
      AdditionalRequestParameterResolver resolver = resolverFactory.create(container);
      resolvers.put(resolver.type(), resolver);
      log.info(
          String.format(
              "Dynamic Registered externals AdditionalRequestParameterResolver %s",
              resolver.getClass().getSimpleName()));
    }

    return resolvers;
  }
}
