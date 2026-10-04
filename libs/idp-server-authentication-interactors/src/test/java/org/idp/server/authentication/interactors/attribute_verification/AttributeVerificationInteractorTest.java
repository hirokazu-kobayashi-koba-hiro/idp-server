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

package org.idp.server.authentication.interactors.attribute_verification;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Issue #1907: the attribute verification interactor. */
class AttributeVerificationInteractorTest {

  /**
   * Two interactions: {@code identity-verified} checks the account and asks for nothing; {@code
   * kba} compares what the end-user enters.
   */
  private static final String CONFIG =
      """
      {
        "id": "4f1c2b3a-0000-4000-8000-000000000001",
        "type": "attribute-verification",
        "interactions": {
          "identity-verified": {
            "execution": {
              "details": {
                "conditions": {
                  "any_of": [[
                    { "path": "$.user.status", "type": "string", "operation": "eq",
                      "value": "IDENTITY_VERIFIED" }
                  ]]
                },
                "error": "identity_verification_required"
              }
            }
          },
          "kba": {
            "execution": {
              "details": {
                "fields": [
                  { "input": "birthdate", "user_attribute": "birthdate", "normalize": "date" },
                  { "input": "phone_last4", "user_attribute": "phone_number",
                    "normalize": "digits", "suffix_length": 4 }
                ],
                "max_attempts": 3,
                "lockout_seconds": 600
              }
            }
          }
        }
      }
      """;

  private static final Map<String, Object> CORRECT =
      Map.of("interaction", "kba", "birthdate", "2000/1/5", "phone_last4", "5678");
  private static final Map<String, Object> WRONG =
      Map.of("interaction", "kba", "birthdate", "2000/1/6", "phone_last4", "5678");
  private static final Map<String, Object> CHECK_ACCOUNT =
      Map.of("interaction", "identity-verified");

  /** Increments a counter per key, as the Redis store does. */
  private static class InMemoryCacheStore implements CacheStore {
    Map<String, Long> counters = new HashMap<>();
    boolean available = true;

    @Override
    public <T> void put(String key, T value) {}

    @Override
    public <T> void put(String key, T value, int timeToLiveSeconds) {}

    @Override
    public <T> Optional<T> find(String key, Class<T> type) {
      return Optional.empty();
    }

    @Override
    public boolean exists(String key) {
      return counters.containsKey(key);
    }

    @Override
    public void delete(String key) {
      counters.remove(key);
    }

    @Override
    public void deleteByPrefix(String prefix) {}

    @Override
    public long increment(String key, int timeToLiveSeconds) {
      if (!available) {
        // What the Redis store returns when it cannot be reached.
        return 0;
      }
      return counters.merge(key, 1L, Long::sum);
    }
  }

  private static class StubConfigurationRepository
      implements AuthenticationConfigurationQueryRepository {
    AuthenticationConfiguration configuration;

    StubConfigurationRepository(AuthenticationConfiguration configuration) {
      this.configuration = configuration;
    }

    @Override
    public AuthenticationConfiguration get(Tenant tenant, String key) {
      return configuration;
    }

    @Override
    public AuthenticationConfiguration find(Tenant tenant, String key) {
      return configuration;
    }

    @Override
    public AuthenticationConfiguration find(
        Tenant tenant, AuthenticationConfigurationIdentifier identifier) {
      return configuration;
    }

    @Override
    public AuthenticationConfiguration findWithDisabled(
        Tenant tenant, AuthenticationConfigurationIdentifier identifier, boolean includeDisabled) {
      return configuration;
    }

    @Override
    public long findTotalCount(Tenant tenant) {
      return 1;
    }

    @Override
    public List<AuthenticationConfiguration> findList(Tenant tenant, int limit, int offset) {
      return List.of(configuration);
    }
  }

  private final InMemoryCacheStore cacheStore = new InMemoryCacheStore();
  private final AttributeVerificationInteractor interactor =
      new AttributeVerificationInteractor(
          new StubConfigurationRepository(
              JsonConverter.snakeCaseInstance().read(CONFIG, AuthenticationConfiguration.class)),
          cacheStore);

  private static Tenant tenant() {
    return new Tenant(
        new TenantIdentifier("2a1b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }

  private static User user() {
    return new User()
        .setSub("user-1")
        .setStatus(UserStatus.REGISTERED)
        .setBirthdate("2000-01-05")
        .setPhoneNumber("090-1234-5678");
  }

  /** A transaction whose user was established by a successful password step. */
  private static AuthenticationTransaction identifiedTransaction() {
    return identifiedTransaction(user());
  }

  private static AuthenticationTransaction identifiedTransaction(User user) {
    Map<String, AuthenticationInteractionResult> results = new HashMap<>();
    results.put(
        "password-authentication",
        new AuthenticationInteractionResult(
            "authentication", "password", 1, 1, 0, LocalDateTime.now()));
    return transaction(user, new AuthenticationInteractionResults(results));
  }

  private static AuthenticationTransaction transaction(
      User user, AuthenticationInteractionResults results) {
    AuthenticationRequest request =
        new AuthenticationRequest(
            StandardAuthFlow.OAUTH.toAuthFlow(),
            null,
            null,
            new RequestedClientId("client"),
            null,
            user,
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(10));
    return new AuthenticationTransaction(
        new AuthenticationTransactionIdentifier("tx"),
        new AuthorizationIdentifier("auth"),
        request,
        new AuthenticationPolicy(),
        results,
        new AuthenticationTransactionAttributes());
  }

  private AuthenticationInteractionRequestResult interact(
      AuthenticationTransaction transaction, Map<String, Object> body) {
    return interactor.interact(
        tenant(),
        transaction,
        StandardAuthenticationInteraction.ATTRIBUTE_VERIFICATION.toType(),
        new AuthenticationInteractionRequest(new HashMap<>(body)),
        null,
        null);
  }

  @Test
  @DisplayName("matching values succeed, recorded as a verification rather than authentication")
  void matches() {
    AuthenticationInteractionRequestResult result = interact(identifiedTransaction(), CORRECT);

    assertEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals(OperationType.VERIFICATION, result.operationType());
    assertFalse(result.operationType().provesPossession());
    assertTrue(result.response().isEmpty());
  }

  @Test
  @DisplayName("a mismatch says only that it did not match, not which item")
  void mismatch() {
    AuthenticationInteractionRequestResult result = interact(identifiedTransaction(), WRONG);

    assertNotEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals("attribute_mismatch", result.response().get("error"));
    assertFalse(result.response().containsKey("field"));
    assertEquals("user-1", result.user().sub());
  }

  @Test
  @DisplayName("a user without the attribute registered is answered like a mismatch")
  void attributeNotRegistered() {
    User withoutBirthdate =
        new User().setSub("user-2").setStatus(UserStatus.REGISTERED).setPhoneNumber("09012345678");
    Map<String, AuthenticationInteractionResult> results = new HashMap<>();
    results.put(
        "password-authentication",
        new AuthenticationInteractionResult(
            "authentication", "password", 1, 1, 0, LocalDateTime.now()));

    AuthenticationInteractionRequestResult result =
        interact(
            transaction(withoutBirthdate, new AuthenticationInteractionResults(results)), CORRECT);

    assertEquals("attribute_mismatch", result.response().get("error"));
  }

  @Test
  @DisplayName("refused before any earlier step has established the user")
  void requiresIdentifiedUser() {
    // A user named by login_hint alone, with no step passed yet.
    AuthenticationInteractionRequestResult result =
        interact(transaction(user(), new AuthenticationInteractionResults()), CORRECT);

    assertNotEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals("invalid_request", result.response().get("error"));
  }

  @Test
  @DisplayName("attempts per user are limited across transactions, and a match clears the count")
  void limitedPerUser() {
    for (int i = 0; i < 3; i++) {
      assertEquals(
          "attribute_mismatch", interact(identifiedTransaction(), WRONG).response().get("error"));
    }

    // A fresh transaction does not reset the count, and even the right answer is refused now.
    assertEquals(
        "too_many_attempts", interact(identifiedTransaction(), CORRECT).response().get("error"));
  }

  @Test
  @DisplayName("a match below the limit clears the per-user count")
  void matchClearsCount() {
    interact(identifiedTransaction(), WRONG);
    interact(identifiedTransaction(), WRONG);
    assertEquals(
        AuthenticationInteractionStatus.SUCCESS,
        interact(identifiedTransaction(), CORRECT).status());

    for (int i = 0; i < 3; i++) {
      assertEquals(
          "attribute_mismatch", interact(identifiedTransaction(), WRONG).response().get("error"));
    }
  }

  @Test
  @DisplayName("without the cache, failures recorded on the transaction still bound the attempts")
  void limitedPerTransactionWithoutCache() {
    cacheStore.available = false;
    AuthenticationTransaction transaction = identifiedTransaction();

    for (int i = 0; i < 3; i++) {
      AuthenticationInteractionRequestResult result = interact(transaction, WRONG);
      assertEquals("attribute_mismatch", result.response().get("error"));
      transaction = transaction.updateWith(result);
    }

    assertEquals("too_many_attempts", interact(transaction, CORRECT).response().get("error"));
  }

  @Test
  @DisplayName(
      "conditions on the account: an identity-verified user passes without entering anything")
  void conditionHolds() {
    User verified = user().setStatus(UserStatus.IDENTITY_VERIFIED);

    AuthenticationInteractionRequestResult result =
        interactor.interact(
            tenant(),
            identifiedTransaction(verified),
            StandardAuthenticationInteraction.ATTRIBUTE_VERIFICATION.toType(),
            new AuthenticationInteractionRequest(new HashMap<>(CHECK_ACCOUNT)),
            null,
            null);

    assertEquals(AuthenticationInteractionStatus.SUCCESS, result.status());
    assertEquals(OperationType.VERIFICATION, result.operationType());
  }

  @Test
  @DisplayName(
      "conditions on the account: the tenant's error is returned at once when they do not hold, and is not counted as a guess")
  void conditionDoesNotHold() {

    for (int i = 0; i < 2; i++) {
      AuthenticationInteractionRequestResult result =
          interactor.interact(
              tenant(),
              identifiedTransaction(),
              StandardAuthenticationInteraction.ATTRIBUTE_VERIFICATION.toType(),
              new AuthenticationInteractionRequest(new HashMap<>(CHECK_ACCOUNT)),
              null,
              null);
      assertEquals(AuthenticationInteractionStatus.CLIENT_ERROR, result.status());
      assertEquals("identity_verification_required", result.response().get("error"));
      assertEquals("identity-verified", result.interactionName());
    }
    assertTrue(cacheStore.counters.isEmpty());
  }

  @Test
  @DisplayName("each interaction is recorded under its own name")
  void guessesRecordedUnderFields() {
    AuthenticationTransaction transaction = identifiedTransaction();

    AuthenticationInteractionRequestResult result = interact(transaction, WRONG);
    transaction = transaction.updateWith(result);

    assertEquals("kba", result.interactionName());
    assertEquals(
        1,
        transaction
            .interactionResults()
            .get("attribute-verification")
            .interactions()
            .get("kba")
            .failureCount());
  }

  @Test
  @DisplayName("a request that names no configured interaction is refused")
  void unknownInteraction() {
    for (Map<String, Object> body :
        List.<Map<String, Object>>of(Map.of(), Map.of("interaction", "no-such-check"))) {
      AuthenticationInteractionRequestResult result = interact(identifiedTransaction(), body);
      assertEquals(AuthenticationInteractionStatus.CLIENT_ERROR, result.status());
      assertEquals("invalid_request", result.response().get("error"));
    }
  }

  @Test
  @DisplayName("an input sent as a number is refused as malformed, without counting as a guess")
  void nonStringInput() {
    AuthenticationTransaction transaction = identifiedTransaction();

    AuthenticationInteractionRequestResult result =
        interact(
            transaction,
            Map.of("interaction", "kba", "birthdate", "2000/1/5", "phone_last4", 5678));
    transaction = transaction.updateWith(result);

    assertEquals("invalid_request", result.response().get("error"));
    assertTrue(cacheStore.counters.isEmpty());
    assertFalse(transaction.interactionResults().get("attribute-verification").hasInteractions());
  }
}
