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

package org.idp.server.core.openid.oauth.clientauthenticator.attestation;

import java.util.Objects;

/**
 * Client Attestation JWT (RFC: draft-ietf-oauth-attestation-based-client-auth)。
 *
 * <p>HTTP ヘッダ {@code OAuth-Client-Attestation} に格納された JWT 文字列を表現する値オブジェクト。 第三者 (Attester)
 * が発行・署名し、クライアントインスタンスの公開鍵 ({@code cnf.jwk}) を含む。
 *
 * <p>必須クレーム (§4.1):
 *
 * <ul>
 *   <li>{@code iss}: Attester の識別子
 *   <li>{@code sub}: クライアント ID
 *   <li>{@code exp}: 有効期限
 *   <li>{@code cnf}: クライアントインスタンスの公開鍵 (JWK 形式)
 * </ul>
 */
public class OAuthClientAttestation {

  private final String value;

  public OAuthClientAttestation() {
    this.value = null;
  }

  public OAuthClientAttestation(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isBlank();
  }
}
