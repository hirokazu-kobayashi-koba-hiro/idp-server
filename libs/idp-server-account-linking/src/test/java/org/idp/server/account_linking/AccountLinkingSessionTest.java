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

package org.idp.server.account_linking;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.idp.server.account_linking.exception.AccountLinkingOperatorMismatchException;
import org.idp.server.account_linking.exception.AccountLinkingSessionStateException;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AccountLinkingSessionTest {

  static final UserIdentifier VICTIM = new UserIdentifier("11111111-1111-1111-1111-111111111111");
  static final UserIdentifier ATTACKER = new UserIdentifier("22222222-2222-2222-2222-222222222222");
  static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 26, 12, 0, 0);

  static AccountLinkingSession pendingSessionFor(UserIdentifier user) {
    return new AccountLinkingSession.Builder()
        .state(new AccountLinkingState("state-value"))
        .tenantIdentifier(new TenantIdentifier("33333333-3333-3333-3333-333333333333"))
        .userIdentifier(user)
        .requestedClientId(new RequestedClientId("rp-client"))
        .provider(new ExternalIdpProvider("google"))
        .redirectUri("https://rp.example.com/linking/callback")
        .requestedScope("openid email")
        .codeVerifier("code-verifier")
        .status(AccountLinkingSessionStatus.PENDING)
        .expiresAt(NOW.plusMinutes(15))
        .build();
  }

  static ParkedCredentials credentialsOf(String federatedUserId) {
    return new ParkedCredentials(
        federatedUserId,
        federatedUserId + "@example.com",
        "openid email",
        new EncryptedData(),
        new EncryptedData(),
        "default",
        NOW.plusHours(1),
        NOW.plusDays(30));
  }

  @Nested
  @DisplayName("正常系")
  class HappyPath {

    @Test
    @DisplayName("pending -> authorized -> parked -> consumed まで進む")
    void fullFlow() {
      AccountLinkingSession pending = pendingSessionFor(VICTIM);

      AccountLinkingBrowserBinding binding = AccountLinkingBrowserBinding.generate();
      AccountLinkingSession authorized =
          pending.authorize(VICTIM, binding, "urn:mace:incommon:iap:silver", "pwd", NOW);
      assertEquals(AccountLinkingSessionStatus.AUTHORIZED, authorized.status());
      assertDoesNotThrow(() -> authorized.verifyBrowserBinding(binding.secret()));

      AccountLinkingSession parked = authorized.park(credentialsOf("victim-google-sub"));
      assertEquals(AccountLinkingSessionStatus.PARKED, parked.status());
      assertTrue(parked.parkedCredentials().exists());

      AccountLinkingSession consumed = parked.consume(VICTIM);
      assertEquals(AccountLinkingSessionStatus.CONSUMED, consumed.status());
    }
  }

  @Nested
  @DisplayName("linking CSRF: 攻撃者が被害者の state を使う向き")
  class ReplayVictimState {

    @Test
    @DisplayName("complete で攻撃者の Bearer は拒否される")
    void attackerCannotClaimVictimSession() {
      AccountLinkingSession parked =
          pendingSessionFor(VICTIM)
              .authorize(VICTIM, AccountLinkingBrowserBinding.generate(), null, "pwd", NOW)
              .park(credentialsOf("victim-google-sub"));

      assertThrows(AccountLinkingOperatorMismatchException.class, () -> parked.consume(ATTACKER));
    }
  }

  @Nested
  @DisplayName("linking CSRF: 攻撃者の state を被害者に踏ませる向き")
  class LureVictimThroughAttackerState {

    @Test
    @DisplayName("/linking/start で被害者が操作者として弾かれる")
    void victimCannotAuthorizeAttackerSession() {
      AccountLinkingSession attackerSession = pendingSessionFor(ATTACKER);

      assertThrows(
          AccountLinkingOperatorMismatchException.class,
          () ->
              attackerSession.authorize(
                  VICTIM, AccountLinkingBrowserBinding.generate(), null, "pwd", NOW));
    }

    @Test
    @DisplayName("start で操作者を検証しないと、被害者の外部アカウントが攻撃者のものになる")
    void withoutOperatorCheckAtStartTheAttackSucceeds() {
      // /linking/start が operator を検証しなかった場合を再現する。
      // 被害者は攻撃者の state のまま外部IdPへ送られ、被害者の外部アカウントで同意する。
      AccountLinkingSession authorizedWithoutCheck =
          pendingSessionFor(ATTACKER).toBuilder()
              .status(AccountLinkingSessionStatus.AUTHORIZED)
              .build();

      AccountLinkingSession parked =
          authorizedWithoutCheck.park(credentialsOf("victim-google-sub"));

      // 攻撃者は state を握っているので、自分の Bearer で claim できてしまう。
      // complete 側の照合（I2）はこの向きを止められない。
      AccountLinkingSession consumed = parked.consume(ATTACKER);

      assertEquals(AccountLinkingSessionStatus.CONSUMED, consumed.status());
      assertEquals(ATTACKER, consumed.userIdentifier());
      assertEquals("victim-google-sub", consumed.parkedCredentials().federatedUserId());
    }
  }

  @Nested
  @DisplayName("linking CSRF: 攻撃者が自分で start を通り、外部IdPのURLだけ被害者に渡す向き")
  class ForwardExternalAuthorizationUrl {

    @Test
    @DisplayName("start を通すのは攻撃者自身なので operator 照合では止まらない")
    void operatorCheckDoesNotCoverThisDirection() {
      AccountLinkingSession attackerSession = pendingSessionFor(ATTACKER);

      // 攻撃者は束縛されたユーザー本人なので、ここは正常に通る。
      // つまり operator 照合はこの向きに対して何も守っていない。
      assertDoesNotThrow(
          () ->
              attackerSession.authorize(
                  ATTACKER, AccountLinkingBrowserBinding.generate(), null, "pwd", NOW));
    }

    @Test
    @DisplayName("binding を持たない被害者のブラウザからの callback は拒否される")
    void callbackWithoutBindingIsRejected() {
      AccountLinkingBrowserBinding attackerBinding = AccountLinkingBrowserBinding.generate();
      AccountLinkingSession authorized =
          pendingSessionFor(ATTACKER).authorize(ATTACKER, attackerBinding, null, "pwd", NOW);

      // 被害者のブラウザには binding cookie が無い。ここで止まるので
      // 被害者の認可コードは交換されない。
      assertThrows(
          AccountLinkingOperatorMismatchException.class,
          () -> authorized.verifyBrowserBinding(null));
      assertThrows(
          AccountLinkingOperatorMismatchException.class,
          () -> authorized.verifyBrowserBinding("guessed-secret"));

      // start を通したブラウザ本人なら通る。
      assertDoesNotThrow(() -> authorized.verifyBrowserBinding(attackerBinding.secret()));
    }
  }

  @Nested
  @DisplayName("単回消費と期限")
  class SingleShotAndExpiry {

    @Test
    @DisplayName("consumed からは再遷移できない")
    void consumedIsTerminal() {
      AccountLinkingSession consumed =
          pendingSessionFor(VICTIM)
              .authorize(VICTIM, AccountLinkingBrowserBinding.generate(), null, "pwd", NOW)
              .park(credentialsOf("victim-google-sub"))
              .consume(VICTIM);

      assertThrows(AccountLinkingSessionStateException.class, () -> consumed.consume(VICTIM));
    }

    @Test
    @DisplayName("authorized を飛ばして park できない")
    void cannotParkFromPending() {
      AccountLinkingSession pending = pendingSessionFor(VICTIM);

      assertThrows(
          AccountLinkingSessionStateException.class,
          () -> pending.park(credentialsOf("victim-google-sub")));
    }

    @Test
    @DisplayName("期限切れは検出される")
    void expiredIsDetected() {
      AccountLinkingSession pending = pendingSessionFor(VICTIM);

      assertTrue(pending.isExpired(NOW.plusMinutes(15)));
      assertThrows(
          AccountLinkingSessionStateException.class,
          () -> pending.verifyNotExpired(NOW.plusMinutes(16)));
      assertDoesNotThrow(() -> pending.verifyNotExpired(NOW));
    }
  }
}
