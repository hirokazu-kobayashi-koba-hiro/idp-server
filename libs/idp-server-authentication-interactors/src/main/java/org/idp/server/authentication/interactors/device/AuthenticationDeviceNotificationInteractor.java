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

package org.idp.server.authentication.interactors.device;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.core.oidc.identity.device.NotificationChannel;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.type.RequestAttributes;

public class AuthenticationDeviceNotificationInteractor implements AuthenticationInteractor {

  AuthenticationDeviceNotifiers authenticationDeviceNotifiers;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;

  public AuthenticationDeviceNotificationInteractor(
      AuthenticationDeviceNotifiers authenticationDeviceNotifiers,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.authenticationDeviceNotifiers = authenticationDeviceNotifiers;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.AUTHENTICATION_DEVICE_NOTIFICATION.toType();
  }

  @Override
  public OperationType operationType() {
    return OperationType.CHALLENGE;
  }

  @Override
  public String method() {
    return "device-notification";
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    try {
      AuthenticationDeviceNotificationConfiguration configuration =
          configurationQueryRepository.get(
              tenant, "authentication-device", AuthenticationDeviceNotificationConfiguration.class);

      User user = transaction.user();
      if (!user.exists()) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "User does not exist");
        DefaultSecurityEventType eventType =
            DefaultSecurityEventType.authentication_device_notification_failure;
        return AuthenticationInteractionRequestResult.clientError(
            response, type, operationType(), method(), eventType);
      }

      AuthenticationDevice authenticationDevice = user.findPrimaryAuthenticationDevice();

      if (!authenticationDevice.exists()) {

        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "User does not have a primary authentication device");
        DefaultSecurityEventType eventType =
            DefaultSecurityEventType.authentication_device_notification_failure;
        return AuthenticationInteractionRequestResult.clientError(
            response, type, operationType(), method(), eventType);
      }

      NotificationChannel channel = new NotificationChannel("fcm");

      AuthenticationDeviceNotifier notifier = authenticationDeviceNotifiers.get(channel);

      notifier.notify(tenant, authenticationDevice, configuration);

      AuthenticationInteractionStatus status = AuthenticationInteractionStatus.SUCCESS;
      Map<String, Object> response = Map.of();
      DefaultSecurityEventType eventType =
          DefaultSecurityEventType.authentication_device_notification_success;
      return new AuthenticationInteractionRequestResult(
          status, type, operationType(), method(), response, eventType);
    } catch (Exception e) {

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", e.getMessage());
      DefaultSecurityEventType eventType =
          DefaultSecurityEventType.authentication_device_notification_failure;
      return AuthenticationInteractionRequestResult.clientError(
          response, type, operationType(), method(), eventType);
    }
  }
}
