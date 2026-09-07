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
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.junit.jupiter.api.Test;

/**
 * Issue #1869: the number-matching code is never sent to the device — the user transcribes it from
 * the sign-in screen. That only proves the approver saw the originating screen if the submission
 * comes from the device the transaction is about, so the step is bound to {@code
 * AuthenticationTransaction#authenticationDevice()}.
 */
class AuthenticationDeviceNumberMatchingInteractorTest {

  private static final String BOUND_DEVICE_ID = "11111111-1111-1111-1111-111111111111";
  private static final String CODE = "4821";

  /** Returns the stored challenge regardless of the key, so the code path under test is reached. */
  private static class StubInteractionQueryRepository
      implements AuthenticationInteractionQueryRepository {
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(
        Tenant tenant, AuthenticationTransactionIdentifier identifier, String key, Class<T> clazz) {
      return (T) new NumberMatchingChallenge(CODE);
    }

    @Override
    public long findTotalCount(Tenant tenant, AuthenticationInteractionQueries queries) {
      return 0;
    }

    @Override
    public List<AuthenticationInteraction> findList(
        Tenant tenant, AuthenticationInteractionQueries queries) {
      return List.of();
    }

    @Override
    public AuthenticationInteraction find(
        Tenant tenant, AuthenticationTransactionIdentifier identifier, String key) {
      return null;
    }
  }

  private final AuthenticationDeviceNumberMatchingInteractor interactor =
      new AuthenticationDeviceNumberMatchingInteractor(new StubInteractionQueryRepository());

  private static AuthenticationTransaction transaction(AuthenticationDevice device) {
    AuthenticationRequest request =
        new AuthenticationRequest(
            StandardAuthFlow.OAUTH.toAuthFlow(),
            null,
            null,
            new RequestedClientId("client"),
            null,
            null,
            device,
            null,
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(10));
    return new AuthenticationTransaction(
        new AuthenticationTransactionIdentifier("tx"),
        new AuthorizationIdentifier("auth"),
        request,
        new AuthenticationPolicy(),
        new AuthenticationTransactionAttributes());
  }

  private static AuthenticationDevice boundDevice() {
    return new AuthenticationDevice(
        BOUND_DEVICE_ID, "app", "Android", "14", "Pixel", "ja", null, null, List.of(), 1);
  }

  private AuthenticationInteractionRequestResult interact(
      AuthenticationDevice device, Map<String, Object> body) {
    return interactor.interact(
        null,
        transaction(device),
        StandardAuthenticationInteraction.AUTHENTICATION_DEVICE_NUMBER_MATCHING.toType(),
        new AuthenticationInteractionRequest(body),
        null,
        null);
  }

  @Test
  void theBoundDeviceWithTheCorrectCodeSucceeds() {
    AuthenticationInteractionRequestResult result =
        interact(boundDevice(), Map.of("device_id", BOUND_DEVICE_ID, "number_matching_code", CODE));

    assertEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
  }

  @Test
  void anotherDeviceIsRejectedEvenWithTheCorrectCode() {
    // The code alone proves nothing about who transcribed it: it is shown on the sign-in screen,
    // so whoever started the flow already has it.
    AuthenticationInteractionRequestResult result =
        interact(
            boundDevice(),
            Map.of(
                "device_id", "22222222-2222-2222-2222-222222222222", "number_matching_code", CODE));

    assertNotEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals(
        "device_id does not match the authentication device",
        result.response().get("error_description"));
  }

  @Test
  void aMissingDeviceIdIsRejected() {
    AuthenticationInteractionRequestResult result =
        interact(boundDevice(), Map.of("number_matching_code", CODE));

    assertNotEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
  }

  @Test
  void aTransactionWithoutABoundDeviceCannotSatisfyNumberMatching() {
    // Fail-closed: with nothing to compare against, passing the step through would leave the
    // verification unbound to any device.
    AuthenticationInteractionRequestResult result =
        interact(
            new AuthenticationDevice(),
            Map.of("device_id", BOUND_DEVICE_ID, "number_matching_code", CODE));

    assertNotEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals(
        "authentication device is not bound to this transaction",
        result.response().get("error_description"));
  }

  @Test
  void theBoundDeviceWithAWrongCodeStillFails() {
    AuthenticationInteractionRequestResult result =
        interact(
            boundDevice(), Map.of("device_id", BOUND_DEVICE_ID, "number_matching_code", "0000"));

    assertNotEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals("number_matching_code does not match", result.response().get("error_description"));
  }
}
