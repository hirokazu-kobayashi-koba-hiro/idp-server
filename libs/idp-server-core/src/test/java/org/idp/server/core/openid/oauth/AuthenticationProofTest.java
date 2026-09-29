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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which proof lets a caller mint an authorization code.
 *
 * <p>Three ways to hold the wrong one, and each has to be refused on its own: the wrong stage, the
 * wrong request, the wrong user. Getting any of them wrong hands someone a code they did not earn.
 */
class AuthenticationProofTest {

  private static final String REQUEST = "5fd1e0ac-3a4e-4a2f-9c2c-0f6a2d3b1e77";
  private static final String SUB = "3f9d5c81-7b24-4e60-9a3f-8c1d2e0b6a75";

  private AuthenticationProof authenticated() {
    return AuthenticationProof.authenticated(REQUEST, SUB);
  }

  private AuthenticationProof completion() {
    return AuthenticationProof.forCompletion(
        REQUEST, SUB, "https://rp.example.com/callback?code=abc");
  }

  @Test
  @DisplayName("認証で得た proof は、同じリクエストの同じ利用者なら通る")
  void acceptsTheProofItWasIssuedFor() {
    assertTrue(authenticated().authorizes(REQUEST, SUB));
  }

  @Test
  @DisplayName("完了用の proof は authorize では使えない")
  void rejectsTheCompletionStage() {
    // 段を跨げると、authorize を経ずに手に入れた値で code を作れてしまう。
    assertFalse(completion().authorizes(REQUEST, SUB));
  }

  @Test
  @DisplayName("別のリクエストで得た proof は使えない")
  void rejectsAnotherRequest() {
    assertFalse(authenticated().authorizes("0c9b4d2e-7a1f-4e88-9b3a-51d6c2f4e900", SUB));
  }

  @Test
  @DisplayName("別の利用者が得た proof は使えない")
  void rejectsAnotherUser() {
    assertFalse(authenticated().authorizes(REQUEST, "8a1c7e35-2d90-4f61-b7e2-9c4d05a3f118"));
  }

  @Test
  @DisplayName("利用者が定まらないまま発行された proof は使えない")
  void rejectsProofWithoutUser() {
    // sub が無い proof は誰のものとも言えない。null 同士が一致してしまわないこと。
    assertFalse(AuthenticationProof.authenticated(REQUEST, null).authorizes(REQUEST, null));
  }
}
