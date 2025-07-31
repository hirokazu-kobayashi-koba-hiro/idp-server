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

package org.idp.server.platform.security.hook.executor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.SecurityEventExecutorsPluginLoader;

public class SecurityEventExecutors {

  private static final SecurityEventExecutors INSTANCE = new SecurityEventExecutors();

  Map<String, SecurityEventExecutor> executors;
  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventExecutors.class);

  public static SecurityEventExecutors getInstance() {
    return INSTANCE;
  }

  private SecurityEventExecutors() {
    this.executors = new HashMap<>();
    SecurityEventNoActionExecutor noActionExecutor = new SecurityEventNoActionExecutor();
    executors.put(noActionExecutor.type(), noActionExecutor);
    List<SecurityEventExecutor> loaded = SecurityEventExecutorsPluginLoader.load();
    for (SecurityEventExecutor securityEventExecutor : loaded) {
      executors.put(securityEventExecutor.type(), securityEventExecutor);
    }
  }

  public SecurityEventExecutor get(String type) {

    SecurityEventExecutor securityEventHookExecutor = executors.get(type);

    if (securityEventHookExecutor == null) {
      log.warn("No SecurityEventHookExecutor found for type: {}", type);
      throw new UnSupportedException("No executor registered for type " + type);
    }

    return securityEventHookExecutor;
  }
}
