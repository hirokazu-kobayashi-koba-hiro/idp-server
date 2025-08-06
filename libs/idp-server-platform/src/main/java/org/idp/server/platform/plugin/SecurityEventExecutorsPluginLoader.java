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

package org.idp.server.platform.plugin;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.hook.executor.SecurityEventExecutor;

public class SecurityEventExecutorsPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(SecurityEventExecutorsPluginLoader.class);

  public static List<SecurityEventExecutor> load() {
    List<SecurityEventExecutor> executors = new ArrayList<>();

    List<SecurityEventExecutor> internalExecutors =
        loadFromInternalModule(SecurityEventExecutor.class);
    for (SecurityEventExecutor executor : internalExecutors) {
      executors.add(executor);
      log.info("Dynamic Registered internal SecurityEventExecutor: {}", executor.type());
    }

    List<SecurityEventExecutor> externalExecutors =
        loadFromExternalModule(SecurityEventExecutor.class);
    for (SecurityEventExecutor executor : externalExecutors) {
      executors.add(executor);
      log.info("Dynamic Registered external SecurityEventExecutor: {}", executor.type());
    }

    return executors;
  }
}
