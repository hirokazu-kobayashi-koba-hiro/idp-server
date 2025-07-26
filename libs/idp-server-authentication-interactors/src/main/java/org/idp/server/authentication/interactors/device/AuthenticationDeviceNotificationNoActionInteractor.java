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

import java.util.Map;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class AuthenticationDeviceNotificationNoActionInteractor
    implements AuthenticationInteractor {

  LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationDeviceNotificationNoActionInteractor.class);

  @Override
  public AuthenticationInteractionType type() {
    return new AuthenticationInteractionType("authentication-device-notification-no-action");
  }

  @Override
  public OperationType operationType() {
    return OperationType.NO_ACTION;
  }

  @Override
  public String method() {
    return "authentication-device-no-action";
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("AuthenticationDeviceNotificationNoActionInteractor called");

    AuthenticationInteractionStatus status = AuthenticationInteractionStatus.SUCCESS;
    Map<String, Object> response = Map.of();
    DefaultSecurityEventType eventType =
        DefaultSecurityEventType.authentication_device_notification_no_action_success;

    return new AuthenticationInteractionRequestResult(
        status, type, operationType(), method(), response, eventType);
  }
}
