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

package org.idp.server.core.openid.oauth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.idp.server.core.openid.authentication.AuthenticationTransactionAttributes;
import org.idp.server.platform.crypto.AesCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which proof lets a caller mint or collect an authorization code.
 *
 * <p>The proof is kept on the authentication transaction of one authorization request, so a proof
 * from another request is never even looked at. What remains to refuse here is the wrong value, the
 * wrong stage and the wrong user, and a value that has already been spent.
 */
class AuthenticationProofTest {

  private static final String SUB = "3f9d5c81-7b24-4e60-9a3f-8c1d2e0b6a75";
  private static final String REDIRECT = "https://rp.example.com/callback?code=abc";
  private static final AesCipher CIPHER =
      new AesCipher(Base64.getEncoder().encodeToString(new byte[32]));

  @Test
  @DisplayName("認証で得た proof は、同じ利用者なら authorize を通る")
  void acceptsTheProofItWasIssuedFor() {
    AuthenticationProof issued = AuthenticationProof.authenticated(SUB);
    AuthenticationTransactionAttributes stored =
        issued.storeOn(new AuthenticationTransactionAttributes(), CIPHER);

    assertTrue(AuthenticationProof.authenticatedOn(stored).authorizes(issued.value(), SUB));
  }

  @Test
  @DisplayName("値そのものは保存されない")
  void storesOnlyTheHash() {
    // DB を読めても proof として使える値は手に入らない。
    AuthenticationProof issued = AuthenticationProof.authenticated(SUB);
    AuthenticationTransactionAttributes stored =
        issued.storeOn(new AuthenticationTransactionAttributes(), CIPHER);

    assertFalse(stored.toMap().toString().contains(issued.value()));
    assertNull(AuthenticationProof.authenticatedOn(stored).value());
  }

  @Test
  @DisplayName("でたらめな値や空の値は通らない")
  void rejectsAnotherValue() {
    AuthenticationTransactionAttributes stored =
        AuthenticationProof.authenticated(SUB)
            .storeOn(new AuthenticationTransactionAttributes(), CIPHER);
    AuthenticationProof proof = AuthenticationProof.authenticatedOn(stored);

    assertFalse(proof.authorizes("not-the-proof", SUB));
    assertFalse(proof.authorizes("", SUB));
    assertFalse(proof.authorizes(null, SUB));
  }

  @Test
  @DisplayName("別の利用者が得た proof は使えない")
  void rejectsAnotherUser() {
    AuthenticationProof issued = AuthenticationProof.authenticated(SUB);
    AuthenticationTransactionAttributes stored =
        issued.storeOn(new AuthenticationTransactionAttributes(), CIPHER);

    assertFalse(
        AuthenticationProof.authenticatedOn(stored)
            .authorizes(issued.value(), "8a1c7e35-2d90-4f61-b7e2-9c4d05a3f118"));
  }

  @Test
  @DisplayName("利用者が定まらないまま発行された proof は使えない")
  void rejectsProofWithoutUser() {
    // sub が無い proof は誰のものとも言えない。null 同士が一致してしまわないこと。
    AuthenticationProof issued = AuthenticationProof.authenticated(null);
    AuthenticationTransactionAttributes stored =
        issued.storeOn(new AuthenticationTransactionAttributes(), CIPHER);

    assertFalse(AuthenticationProof.authenticatedOn(stored).authorizes(issued.value(), null));
  }

  @Test
  @DisplayName("新しく発行すると、前の proof は使えなくなる")
  void onlyTheLatestCounts() {
    AuthenticationProof first = AuthenticationProof.authenticated(SUB);
    AuthenticationProof second = AuthenticationProof.authenticated(SUB);
    AuthenticationTransactionAttributes stored =
        second.storeOn(first.storeOn(new AuthenticationTransactionAttributes(), CIPHER), CIPHER);

    assertFalse(AuthenticationProof.authenticatedOn(stored).authorizes(first.value(), SUB));
    assertTrue(AuthenticationProof.authenticatedOn(stored).authorizes(second.value(), SUB));
  }

  @Test
  @DisplayName("使った proof は消える")
  void spentProofIsGone() {
    AuthenticationProof issued = AuthenticationProof.authenticated(SUB);
    AuthenticationTransactionAttributes stored =
        issued.storeOn(new AuthenticationTransactionAttributes(), CIPHER);
    AuthenticationTransactionAttributes spent =
        AuthenticationProof.authenticatedOn(stored).spendOn(stored);

    assertFalse(AuthenticationProof.authenticatedOn(spent).authorizes(issued.value(), SUB));
  }

  @Test
  @DisplayName("完了用の proof は /complete を通り、遷移先を持ち運ぶ")
  void completionCarriesTheRedirect() {
    AuthenticationProof issued = AuthenticationProof.forCompletion(SUB, REDIRECT);
    AuthenticationTransactionAttributes stored =
        issued.storeOn(new AuthenticationTransactionAttributes(), CIPHER);

    AuthenticationProof proof = AuthenticationProof.completionOn(stored, CIPHER);
    assertTrue(proof.completes(issued.value()));
    assertEquals(REDIRECT, proof.redirectUri());
  }

  @Test
  @DisplayName("2 つの段は取り違えられない")
  void stagesAreNotInterchangeable() {
    // 段を跨げると、authorize を経ずに code を受け取れてしまう。
    AuthenticationProof authenticated = AuthenticationProof.authenticated(SUB);
    AuthenticationProof completion = AuthenticationProof.forCompletion(SUB, REDIRECT);
    AuthenticationTransactionAttributes stored =
        completion.storeOn(
            authenticated.storeOn(new AuthenticationTransactionAttributes(), CIPHER), CIPHER);

    assertFalse(AuthenticationProof.completionOn(stored, CIPHER).completes(authenticated.value()));
    assertFalse(AuthenticationProof.authenticatedOn(stored).authorizes(completion.value(), SUB));
  }

  @Test
  @DisplayName("完了用の proof の遷移先（code を含む）は暗号化して保存される")
  void completionRedirectIsEncrypted() {
    // 遷移先には認可コードが入り、hybrid ならトークンも入る。平文で DB に残さない。
    AuthenticationTransactionAttributes stored =
        AuthenticationProof.forCompletion(SUB, REDIRECT)
            .storeOn(new AuthenticationTransactionAttributes(), CIPHER);

    assertFalse(stored.toMap().toString().contains("code=abc"));
    assertEquals(REDIRECT, AuthenticationProof.completionOn(stored, CIPHER).redirectUri());
  }
}
