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

package org.idp.server.authentication.interactors.password;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.identity.User;
import org.junit.jupiter.api.Test;

/**
 * Issue #1862: the {@code execution} of a password interaction must receive the same allow-listed
 * {@code $.user.*} projection that {@code ExternalApiAuthenticationInteractor} sends (#1439) and
 * that {@code user_resolve} already receives (#1767).
 */
class PasswordAuthenticationInteractorExecutionRequestTest {

  PasswordAuthenticationInteractor interactor = new PasswordAuthenticationInteractor(null, null);

  private User authenticatedUser() {
    HashMap<String, Object> customProperties = new HashMap<>();
    customProperties.put("member_number", "M-0001");
    return new User()
        .setSub("victim-sub")
        .setProviderId("idp-server")
        .setPreferredUsername("victim")
        .setCustomProperties(customProperties);
  }

  @Test
  void secondFactorForwardsAllowListedUserProjection() {
    AuthenticationInteractionRequest request =
        new AuthenticationInteractionRequest(Map.of("password", "secret"));

    AuthenticationExecutionRequest executionRequest =
        interactor.buildExecutionRequest(request, authenticatedUser(), true, true);

    assertTrue(executionRequest.hasTransactionUser());

    Map<String, Object> projection = executionRequest.transactionUser();
    assertEquals("victim-sub", projection.get("sub"));
    assertEquals("idp-server", projection.get("provider_id"));

    @SuppressWarnings("unchecked")
    Map<String, Object> customProperties =
        (Map<String, Object>) projection.get("custom_properties");
    assertEquals("M-0001", customProperties.get("member_number"));

    // The projection is an allow list: secrets and evaluation-only signals never leave the process.
    assertFalse(projection.containsKey("hashed_password"));
    assertFalse(projection.containsKey("status"));
    assertFalse(projection.containsKey("verified_claims"));
  }

  @Test
  void firstFactorLeavesTheProjectionEmpty() {
    // With no user in the transaction the executor must omit $.user exactly as it did before Issue
    // #1862 — existing configurations keep behaving the same.
    AuthenticationInteractionRequest request =
        new AuthenticationInteractionRequest(Map.of("username", "victim", "password", "secret"));

    AuthenticationExecutionRequest executionRequest =
        interactor.buildExecutionRequest(request, User.notFound(), false, false);

    assertFalse(executionRequest.hasTransactionUser());
    assertEquals("victim", executionRequest.toMap().get("username"));
  }

  @Test
  void anUnverifiedHintUserIsNotProjectedEvenOnARequiresUserStep() {
    // A login_hint on the authorization endpoint fills the transaction user with no client
    // authentication, so an unverified caller could name a victim and have the victim's
    // allow-listed attributes sent to the configured external API. The policy still says this step
    // requires a user (secondFactor), but who established that user is a separate question.
    AuthenticationInteractionRequest request =
        new AuthenticationInteractionRequest(Map.of("username", "attacker", "password", "secret"));

    AuthenticationExecutionRequest executionRequest =
        interactor.buildExecutionRequest(request, authenticatedUser(), true, false);

    assertFalse(executionRequest.hasTransactionUser());
    // The #1396 pinning is independent and still applies.
    assertEquals("victim", executionRequest.toMap().get("username"));
  }

  @Test
  void secondFactorPinsUsernameToTheAuthenticatedUser() {
    // Issue #1396: the request-supplied username must not select the account being verified.
    AuthenticationInteractionRequest request =
        new AuthenticationInteractionRequest(
            Map.of("username", "attacker", "provider_id", "evil-idp", "password", "secret"));

    AuthenticationExecutionRequest executionRequest =
        interactor.buildExecutionRequest(request, authenticatedUser(), true, true);

    assertEquals("victim", executionRequest.toMap().get("username"));
    assertEquals("idp-server", executionRequest.toMap().get("provider_id"));
  }

  @Test
  void requestBodyCannotSpoofTheProjection() {
    // The projection is kept out of the request values, so a "user" key in the submitted body
    // cannot masquerade as the server-injected $.user.
    AuthenticationInteractionRequest request =
        new AuthenticationInteractionRequest(
            Map.of("password", "secret", "user", Map.of("sub", "attacker-sub")));

    AuthenticationExecutionRequest executionRequest =
        interactor.buildExecutionRequest(request, authenticatedUser(), true, true);

    assertEquals("victim-sub", executionRequest.transactionUser().get("sub"));
  }
}
