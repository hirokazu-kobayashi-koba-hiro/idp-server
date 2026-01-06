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
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.type.ciba.BindingMessage;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class AuthenticationDeviceBindingMessageInteractor implements AuthenticationInteractor {

  LoggerWrapper log = LoggerWrapper.getLogger(AuthenticationDeviceBindingMessageInteractor.class);

  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.AUTHENTICATION_DEVICE_BINDING_MESSAGE.toType();
  }

  @Override
  public boolean isBrowserBased() {
    return false;
  }

  @Override
  public String method() {
    return "binding-message";
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
      log.debug("AuthenticationDeviceBindingMessageInteractor called");

      AuthenticationContext authenticationContext = transaction.requestContext();
      BindingMessage bindingMessage = authenticationContext.bindingMessage();

      if (bindingMessage == null) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "Binding Message is null");

        DefaultSecurityEventType eventType =
            DefaultSecurityEventType.authentication_device_binding_message_failure;

        return AuthenticationInteractionRequestResult.clientError(
            response, type, operationType(), method(), eventType);
      }

      String bindingMessageValue = request.getValueAsString("binding_message");
      if (!bindingMessage.value().equals(bindingMessageValue)) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "Binding Message is unmatched");

        DefaultSecurityEventType eventType =
            DefaultSecurityEventType.authentication_device_binding_message_failure;

        return AuthenticationInteractionRequestResult.clientError(
            response, type, operationType(), method(), eventType);
      }

      AuthenticationInteractionStatus status = AuthenticationInteractionStatus.SUCCESS;
      Map<String, Object> response = Map.of();
      DefaultSecurityEventType eventType =
          DefaultSecurityEventType.authentication_device_binding_message_success;

      return new AuthenticationInteractionRequestResult(
          status, type, operationType(), method(), response, eventType);
    } catch (IllegalArgumentException validationException) {
      // Issue #1008: Handle validation errors from getValueAsString()
      log.warn("Request validation failed: {}", validationException.getMessage());

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", validationException.getMessage());

      return AuthenticationInteractionRequestResult.clientError(
          response,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.authentication_device_binding_message_failure);
    }
  }
}
