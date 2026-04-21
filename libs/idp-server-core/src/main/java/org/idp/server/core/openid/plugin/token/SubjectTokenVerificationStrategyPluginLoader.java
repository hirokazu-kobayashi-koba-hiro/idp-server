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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.SubjectTokenType;
import org.idp.server.core.openid.token.verifier.SubjectTokenVerificationStrategy;
import org.idp.server.core.openid.token.verifier.SubjectTokenVerificationStrategyFactory;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class SubjectTokenVerificationStrategyPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(SubjectTokenVerificationStrategyPluginLoader.class);

  public static Map<SubjectTokenType, SubjectTokenVerificationStrategy> load(
      ApplicationComponentContainer container) {
    Map<SubjectTokenType, SubjectTokenVerificationStrategy> strategyMap = new HashMap<>();

    List<SubjectTokenVerificationStrategyFactory> internals =
        loadFromInternalModule(SubjectTokenVerificationStrategyFactory.class);
    for (SubjectTokenVerificationStrategyFactory factory : internals) {
      SubjectTokenVerificationStrategy strategy = factory.create(container);
      log.info(
          "Dynamic Registered internal SubjectTokenVerificationStrategy "
              + strategy.getClass().getSimpleName());
      strategyMap.put(strategy.type(), strategy);
    }

    List<SubjectTokenVerificationStrategyFactory> externals =
        loadFromExternalModule(SubjectTokenVerificationStrategyFactory.class);
    for (SubjectTokenVerificationStrategyFactory factory : externals) {
      SubjectTokenVerificationStrategy strategy = factory.create(container);
      log.info(
          "Dynamic Registered external SubjectTokenVerificationStrategy "
              + strategy.getClass().getSimpleName());
      strategyMap.put(strategy.type(), strategy);
    }

    return strategyMap;
  }
}
