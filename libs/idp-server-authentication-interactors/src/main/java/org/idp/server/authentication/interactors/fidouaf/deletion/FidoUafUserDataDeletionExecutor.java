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
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.openid.identity.event.UserLifecycleEventResult;
import org.idp.server.core.openid.identity.event.UserLifecycleType;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class FidoUafUserDataDeletionExecutor implements UserLifecycleEventExecutor {

  AuthenticationExecutors authenticationExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(FidoUafUserDataDeletionExecutor.class);

  public FidoUafUserDataDeletionExecutor(
      AuthenticationExecutors authenticationExecutors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.authenticationExecutors = authenticationExecutors;
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
    if (userLifecycleEvent.lifecycleType() != UserLifecycleType.DELETE) {
      return false;
    }

    Tenant tenant = userLifecycleEvent.tenant();
    AuthenticationConfiguration authenticationConfiguration =
        configurationQueryRepository.find(tenant, "fido-uaf");

    if (!authenticationConfiguration.exists()) {
      log.info(
          "FidoUafUserDataDeletionExecutor, Authentication interaction config 'fido-uaf' not found");
      return false;
    }

    AuthenticationInteractionConfig authenticationInteractionConfig =
        authenticationConfiguration.getAuthenticationConfig("fido-uaf-deregistration");

    if (authenticationInteractionConfig == null) {
      log.info(
          "FidoUafUserDataDeletionExecutor, Authentication interaction config 'fido-uaf-deregistration' not found");
      return false;
    }

    User user = userLifecycleEvent.user();

    return user.enabledFidoUaf();
  }

  @Override
  public UserLifecycleEventResult execute(UserLifecycleEvent userLifecycleEvent) {
    try {
      Tenant tenant = userLifecycleEvent.tenant();
      AuthenticationConfiguration authenticationConfiguration =
          configurationQueryRepository.get(tenant, "fido-uaf");

      AuthenticationInteractionConfig authenticationInteractionConfig =
          authenticationConfiguration.getAuthenticationConfig("fido-uaf-deregistration");
      AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();
      AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

      User user = userLifecycleEvent.user();
      Map<String, Object> data = new HashMap<>();
      boolean result = true;
      for (AuthenticationDevice authenticationDevice : user.authenticationDevices()) {
        if (!authenticationDevice.enabledFidoUaf()) {
          log.debug(
              "Skip delete fido-uaf. device '{}' because it is not enabled fido-uaf",
              authenticationDevice.id());
          continue;
        }

        log.info("Delete fido-uaf for device '{}'", authenticationDevice.id());
        Map<String, Object> request = new HashMap<>();
        request.put("device_id", authenticationDevice.id());
        AuthenticationExecutionRequest fidoUafExecutionRequest =
            new AuthenticationExecutionRequest(request);
        AuthenticationExecutionResult executionResult =
            executor.execute(
                tenant,
                new AuthenticationTransactionIdentifier(),
                fidoUafExecutionRequest,
                new RequestAttributes(),
                execution);
        data.put(authenticationDevice.id(), executionResult.toMap());
        if (!executionResult.isSuccess()) {
          result = false;
        }
      }

      if (!result) {
        return UserLifecycleEventResult.failure(name(), data);
      }

      return UserLifecycleEventResult.success(name(), data);
    } catch (Exception e) {
      log.error("UserLifecycleEventExecutor error: {}", e.getMessage(), e);

      Map<String, Object> data = new HashMap<>();
      data.put("message", e.getMessage());
      return UserLifecycleEventResult.failure(name(), data);
    }
  }
}
