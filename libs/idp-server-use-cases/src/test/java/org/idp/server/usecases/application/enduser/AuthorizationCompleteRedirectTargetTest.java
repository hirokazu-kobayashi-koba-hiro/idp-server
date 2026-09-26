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

package org.idp.server.usecases.application.enduser;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where {@code /complete} is allowed to send the browser.
 *
 * <p>The target is no longer taken from the query string — it travels inside the proof, which this
 * server issued. The comparison is therefore defence in depth rather than input validation, and it
 * is kept because the cost of a wrong answer here is a redirect this server vouches for, which is
 * the whole value of an open redirect to a phisher.
 *
 * <p>It was first written as a prefix test, which has no boundary. The cases below are the ones a
 * prefix test lets through, plus the ones where there is nothing to compare against.
 */
class AuthorizationCompleteRedirectTargetTest {

  private static final String REGISTERED = "https://sample.idp.local/api/auth/callback/idp-server";
  private static final String ORIGIN_ONLY = "http://localhost:3000";

  private boolean accepts(String to, String registered) {
    return OAuthFlowEntryService.addressesSameTarget(to, registered);
  }

  @Test
  @DisplayName("認可応答そのもの（code / state 付き）は通る")
  void acceptsTheAuthorizationResponse() {
    assertTrue(accepts(REGISTERED + "?code=abc&state=xyz", REGISTERED));
  }

  @Test
  @DisplayName("登録値そのままも通る")
  void acceptsTheRegisteredUriItself() {
    assertTrue(accepts(REGISTERED, REGISTERED));
  }

  @Test
  @DisplayName("オリジンだけ登録したクライアントで、ホストを伸ばした先は弾く")
  void rejectsHostExtensionOnOriginOnlyRegistration() {
    // prefix 比較だとここが通ってしまう。
    assertFalse(accepts("http://localhost:3000.attacker.example/", ORIGIN_ONLY));
  }

  @Test
  @DisplayName("別ホストは弾く")
  void rejectsDifferentHost() {
    assertFalse(accepts("https://attacker.example/api/auth/callback/idp-server", REGISTERED));
  }

  @Test
  @DisplayName("userinfo を使って登録ホストに見せかけたものは弾く")
  void rejectsUserInfo() {
    assertFalse(accepts("https://sample.idp.local@attacker.example/", REGISTERED));
    assertFalse(
        accepts(
            "https://attacker.example@sample.idp.local/api/auth/callback/idp-server", REGISTERED));
  }

  @Test
  @DisplayName("パスが違えば弾く")
  void rejectsDifferentPath() {
    assertFalse(accepts("https://sample.idp.local/api/auth/callback/idp-server-evil", REGISTERED));
    assertFalse(accepts("https://sample.idp.local/", REGISTERED));
  }

  @Test
  @DisplayName("scheme が違えば弾く")
  void rejectsDifferentScheme() {
    assertFalse(accepts("http://sample.idp.local/api/auth/callback/idp-server", REGISTERED));
  }

  @Test
  @DisplayName("ホストの大文字小文字は同じ場所として扱う")
  void hostIsCaseInsensitive() {
    assertTrue(accepts("https://SAMPLE.IDP.LOCAL/api/auth/callback/idp-server", REGISTERED));
  }

  @Test
  @DisplayName("null / 空 / 壊れた URI は弾く")
  void rejectsUnusable() {
    assertFalse(accepts(null, REGISTERED));
    assertFalse(accepts("", REGISTERED));
    assertFalse(accepts("ht tp://broken", REGISTERED));
  }

  @Test
  @DisplayName("登録値が無い場合も落ちずに弾く")
  void rejectsMissingRegisteredValue() {
    // redirect_uri は OAuth 2.0 のリクエストでは省略でき、そのとき値は null で届く。
    // 比較できないことと、比較して一致しないことは同じ扱いでよいが、落ちてはいけない。
    assertFalse(accepts(REGISTERED, null));
    assertFalse(accepts(REGISTERED, ""));
  }
}
