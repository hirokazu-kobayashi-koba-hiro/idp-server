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

package org.idp.server.core.openid.oauth.dpop;

import java.util.List;

/**
 * Validates the {@code DPoP} HTTP request header presence rules.
 *
 * <p>RFC 9449 Section 4.3 (Check 1): the request MUST NOT contain more than one DPoP header field.
 * This validator inspects the raw header values supplied by the transport layer and rejects
 * requests with multiple DPoP headers via {@link DPoPProofInvalidException}, which is mapped to the
 * {@code invalid_dpop_proof} error response.
 *
 * <p>Modeled after {@link org.idp.server.core.openid.oauth.validator.OAuthRequestValidator} for
 * consistency: validators encapsulate protocol-level rules and live in the core layer.
 *
 * <h3>{@code headerValues} のセマンティクス</h3>
 *
 * <p>{@code DPoP} ヘッダの状態は次の 3 通りで、すべて RFC 9449 §4.3 Check 1 違反 (=複数ヘッダ) ではない:
 *
 * <ul>
 *   <li>{@code null} — Spring の {@code @RequestHeader(required=false)} などからヘッダ未送信時に 渡される値。DPoP
 *       自体が任意であるエンドポイント (token endpoint 等) では正常系。
 *   <li>空リスト ({@code List.of()}) — フレームワーク側が「ヘッダ無し」を空リストで表現する場合。 挙動は {@code null} と同じ扱い。
 *   <li>単一要素リスト ({@code size() == 1}) — 通常の DPoP 提示。値そのものの妥当性は本クラスでは 検証せず、後段の {@link
 *       DPoPProofVerifier} が JWS 形式・iat/nbf/jwk 等を検証する。
 * </ul>
 *
 * <p>本クラスはあくまで「<b>複数</b>ヘッダの検出」のみを責務とするため、上記いずれの「無し / 単一」状態 も silently accept する。DPoP の必須性 (=
 * 提示が無いと拒否) は、この validator ではなく proof 検証を呼び出す側で判断する。
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.3">RFC 9449 Section 4.3</a>
 */
public class DPoPHeaderValidator {

  List<String> headerValues;

  /**
   * @param headerValues DPoP ヘッダ値のリスト。{@code null} または空リストはヘッダ未提示とみなす (上記クラス Javadoc の
   *     "headerValues のセマンティクス" を参照)。
   */
  public DPoPHeaderValidator(List<String> headerValues) {
    this.headerValues = headerValues;
  }

  /**
   * Validates the DPoP header presence rules.
   *
   * @throws DPoPProofInvalidException when more than one DPoP header value is supplied
   */
  public void validate() {
    throwExceptionIfMultipleHeaders();
  }

  void throwExceptionIfMultipleHeaders() {
    if (headerValues != null && headerValues.size() > 1) {
      throw new DPoPProofInvalidException(
          "request contains multiple DPoP headers (RFC 9449 Section 4.3)");
    }
  }
}
