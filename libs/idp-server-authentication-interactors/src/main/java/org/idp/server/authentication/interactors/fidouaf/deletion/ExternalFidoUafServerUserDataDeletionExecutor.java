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


package org.idp.server.authentication.interactors.fidouaf.deletion;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.fidouaf.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.event.UserLifecycleEvent;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventResult;
import org.idp.server.core.oidc.identity.event.UserLifecycleType;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ExternalFidoUafServerUserDataDeletionExecutor implements UserLifecycleEventExecutor {

  FidoUafExecutors fidoUafExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(ExternalFidoUafServerUserDataDeletionExecutor.class);

  public ExternalFidoUafServerUserDataDeletionExecutor(
      FidoUafExecutors fidoUafExecutors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.fidoUafExecutors = fidoUafExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public UserLifecycleType lifecycleType() {
    return UserLifecycleType.DELETE;
  }

  @Override
  public String name() {
    return "fido-uaf-deletion";
  }

  @Override
  public boolean shouldExecute(UserLifecycleEvent userLifecycleEvent) {
    if (userLifecycleEvent.lifecycleType() == UserLifecycleType.DELETE) {
      User user = userLifecycleEvent.user();
      return user.enabledFidoUaf();
    }
    return false;
  }

  @Override
  public UserLifecycleEventResult execute(UserLifecycleEvent userLifecycleEvent) {
    try {
      Tenant tenant = userLifecycleEvent.tenant();
      FidoUafConfiguration fidoUafConfiguration =
          configurationQueryRepository.get(tenant, "fido-uaf", FidoUafConfiguration.class);

      FidoUafExecutor fidoUafExecutor = fidoUafExecutors.get(fidoUafConfiguration.type());

      Map<String, Object> request = new HashMap<>();
      // TODO dynamic mapping request
      FidoUafExecutionRequest fidoUafExecutionRequest = new FidoUafExecutionRequest(request);
      FidoUafExecutionResult executionResult =
          fidoUafExecutor.deleteKey(tenant, fidoUafExecutionRequest, fidoUafConfiguration);

      if (!executionResult.isSuccess()) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", executionResult.status());
        data.put("contents", executionResult.contents());
        return UserLifecycleEventResult.failure(name(), data);
      }

      return UserLifecycleEventResult.success(name(), executionResult.contents());
    } catch (Exception e) {
      Map<String, Object> data = new HashMap<>();
      data.put("message", e.getMessage());
      return UserLifecycleEventResult.failure(name(), data);
    }
  }
}
