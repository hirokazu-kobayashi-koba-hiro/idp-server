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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationExecutor;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationExecutorFactory;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationExecutors;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationType;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class SmsAuthenticationExecutorPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(SmsAuthenticationExecutorPluginLoader.class);

  public static SmsAuthenticationExecutors load(AuthenticationDependencyContainer container) {
    Map<SmsAuthenticationType, SmsAuthenticationExecutor> executors = new HashMap<>();

    List<SmsAuthenticationExecutorFactory> internals =
        loadFromInternalModule(SmsAuthenticationExecutorFactory.class);
    for (SmsAuthenticationExecutorFactory factory : internals) {
      SmsAuthenticationExecutor executor = factory.create(container);
      executors.put(executor.type(), executor);
      log.info("Dynamic Registered internal SmsAuthenticationExecutor " + executor.type().name());
    }

    List<SmsAuthenticationExecutorFactory> externals =
        loadFromExternalModule(SmsAuthenticationExecutorFactory.class);
    for (SmsAuthenticationExecutorFactory factory : externals) {
      SmsAuthenticationExecutor executor = factory.create(container);
      executors.put(executor.type(), executor);
      log.info("Dynamic Registered external SmsAuthenticationExecutor " + executor.type().name());
    }

    return new SmsAuthenticationExecutors(executors);
  }
}
