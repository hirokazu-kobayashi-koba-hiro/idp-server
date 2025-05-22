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


package org.idp.server.platform.dependency;

import java.util.ServiceLoader;
import org.idp.server.platform.log.LoggerWrapper;

public class ApplicationComponentContainerLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(ApplicationComponentContainerLoader.class);

  public static ApplicationComponentContainer load(
      ApplicationComponentDependencyContainer dependencyContainer) {
    ApplicationComponentContainer container = new ApplicationComponentContainer();
    ServiceLoader<ApplicationComponentProvider> loader =
        ServiceLoader.load(ApplicationComponentProvider.class);

    for (ApplicationComponentProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide(dependencyContainer));
      log.info("Dynamic Registered application component " + provider.type());
    }

    return container;
  }
}
