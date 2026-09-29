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

package org.idp.server.core.openid.authentication;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether a cross-site authorization has to present an {@code auth_proof} at {@code /authorize}.
 *
 * <p>The answer comes from the transaction alone, so it does not change when the proof store is
 * unavailable.
 */
class AuthenticationTransactionBrowserPossessionTest {

  private static final String PASSWORD = "password-authentication";
  private static final String FIDO_UAF = "fido-uaf-authentication";
  private static final String FIDO_UAF_CHALLENGE = "fido-uaf-authentication-challenge";
  private static final String FEDERATION = "oidc-google";

  private final AuthenticationInteractors interactors =
      new AuthenticationInteractors(
          Map.of(
              new AuthenticationInteractionType(PASSWORD), interactor(PASSWORD, true),
              new AuthenticationInteractionType(FIDO_UAF), interactor(FIDO_UAF, false),
              new AuthenticationInteractionType(FIDO_UAF_CHALLENGE),
                  interactor(FIDO_UAF_CHALLENGE, false)));

  @Test
  @DisplayName("ブラウザでのパスワード認証が成功していれば proof が要る")
  void browserAuthenticationCounts() {
    assertTrue(
        transaction(Map.of(PASSWORD, result("AUTHENTICATION", 1)))
            .browserProvedPossession(interactors));
  }

  @Test
  @DisplayName("デバイスだけで認証していれば要らない")
  void deviceOnlyDoesNotCount() {
    assertFalse(
        transaction(
                Map.of(
                    FIDO_UAF_CHALLENGE, result("CHALLENGE", 1),
                    FIDO_UAF, result("AUTHENTICATION", 1)))
            .browserProvedPossession(interactors));
  }

  @Test
  @DisplayName("パスワードのあとにデバイスが続いても、ブラウザのステップがあったので要る")
  void browserStepBeforeDeviceCounts() {
    assertTrue(
        transaction(
                Map.of(
                    PASSWORD, result("AUTHENTICATION", 1),
                    FIDO_UAF, result("AUTHENTICATION", 1)))
            .browserProvedPossession(interactors));
  }

  @Test
  @DisplayName("失敗しただけのブラウザのステップは数えない")
  void failedBrowserStepDoesNotCount() {
    assertFalse(
        transaction(Map.of(PASSWORD, result("AUTHENTICATION", 0)))
            .browserProvedPossession(interactors));
  }

  @Test
  @DisplayName("challenge のように本人しか通せないとは言えないステップは数えない")
  void nonPossessionStepDoesNotCount() {
    assertFalse(
        transaction(Map.of(PASSWORD, result("CHALLENGE", 1))).browserProvedPossession(interactors));
  }

  @Test
  @DisplayName("インタラクターの無い種類（フェデレーション）はブラウザのステップとして数える")
  void unknownTypeKeepsProofRequired() {
    assertTrue(
        transaction(Map.of(FEDERATION, result("AUTHENTICATION", 1)))
            .browserProvedPossession(interactors));
  }

  private AuthenticationTransaction transaction(
      Map<String, AuthenticationInteractionResult> results) {
    return new AuthenticationTransaction(
        new AuthenticationTransactionIdentifier(UUID.randomUUID().toString()),
        new AuthorizationIdentifier(UUID.randomUUID().toString()),
        new AuthenticationRequest(),
        null,
        new AuthenticationInteractionResults(new HashMap<>(results)),
        new AuthenticationTransactionAttributes());
  }

  private AuthenticationInteractionResult result(String operationType, int successCount) {
    return new AuthenticationInteractionResult(
        operationType, "test", 1, successCount, 1 - successCount, LocalDateTime.now());
  }

  private static AuthenticationInteractor interactor(String type, boolean browserBased) {
    return new AuthenticationInteractor() {
      @Override
      public AuthenticationInteractionType type() {
        return new AuthenticationInteractionType(type);
      }

      @Override
      public boolean isBrowserBased() {
        return browserBased;
      }

      @Override
      public String method() {
        return type;
      }

      @Override
      public AuthenticationInteractionRequestResult interact(
          Tenant tenant,
          AuthenticationTransaction transaction,
          AuthenticationInteractionType interactionType,
          AuthenticationInteractionRequest request,
          RequestAttributes requestAttributes,
          UserQueryRepository userQueryRepository) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
