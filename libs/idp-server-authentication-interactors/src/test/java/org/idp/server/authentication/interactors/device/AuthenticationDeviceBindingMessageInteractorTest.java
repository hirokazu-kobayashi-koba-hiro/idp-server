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

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.ciba.BindingMessage;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.junit.jupiter.api.Test;

/**
 * Issue #1950: the binding message step compares what the device submits with the binding message
 * of the transaction. A transaction that has none — an authorization code flow, or a CIBA request
 * sent without {@code binding_message} — holds an empty value, so the step has nothing to compare
 * against and must not pass.
 */
class AuthenticationDeviceBindingMessageInteractorTest {

  private final AuthenticationDeviceBindingMessageInteractor interactor =
      new AuthenticationDeviceBindingMessageInteractor();

  private static AuthenticationTransaction transaction(BindingMessage bindingMessage) {
    AuthenticationRequest request =
        new AuthenticationRequest(
            StandardAuthFlow.CIBA.toAuthFlow(),
            null,
            null,
            new RequestedClientId("client"),
            null,
            null,
            null,
            new AuthenticationContext(null, null, bindingMessage, null),
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(10));
    return new AuthenticationTransaction(
        new AuthenticationTransactionIdentifier("tx"),
        new AuthorizationIdentifier("auth"),
        request,
        new AuthenticationPolicy(),
        new AuthenticationTransactionAttributes());
  }

  private AuthenticationInteractionRequestResult interact(
      BindingMessage bindingMessage, Map<String, Object> body) {
    return interactor.interact(
        null,
        transaction(bindingMessage),
        StandardAuthenticationInteraction.AUTHENTICATION_DEVICE_BINDING_MESSAGE.toType(),
        new AuthenticationInteractionRequest(body),
        null,
        null);
  }

  private static Map<String, Object> submitting(String bindingMessage) {
    Map<String, Object> body = new HashMap<>();
    body.put("binding_message", bindingMessage);
    return body;
  }

  @Test
  void theMatchingBindingMessageSucceeds() {
    AuthenticationInteractionRequestResult result =
        interact(new BindingMessage("999"), submitting("999"));

    assertEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
  }

  @Test
  void anotherBindingMessageFails() {
    AuthenticationInteractionRequestResult result =
        interact(new BindingMessage("999"), submitting("000"));

    assertNotEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
  }

  /** How a transaction without a binding message is read back from storage. */
  @Test
  void anEmptyBindingMessageCannotBeMatched() {
    AuthenticationInteractionRequestResult result =
        interact(new BindingMessage(""), submitting(""));

    assertNotEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals("invalid_request", result.response().get("error"));
  }

  /** How a transaction without a binding message is created, before it is stored. */
  @Test
  void anUnsetBindingMessageCannotBeMatched() {
    AuthenticationInteractionRequestResult result = interact(new BindingMessage(), submitting(""));

    assertNotEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals("invalid_request", result.response().get("error"));
  }

  @Test
  void aMissingBindingMessageObjectFails() {
    AuthenticationInteractionRequestResult result = interact(null, submitting(""));

    assertNotEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
  }
}
