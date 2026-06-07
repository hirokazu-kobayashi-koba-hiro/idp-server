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
 * Client Attestation Proof of Possession (PoP) JWT。
 *
 * <p>HTTP ヘッダ {@code OAuth-Client-Attestation-PoP} に格納された JWT 文字列を表現する値オブジェクト。 クライアントインスタンスが
 * Attestation の {@code cnf.jwk} に紐づく秘密鍵で署名する。
 *
 * <p>必須クレーム (§4.2):
 *
 * <ul>
 *   <li>{@code aud}: AS の issuer 識別子
 *   <li>{@code jti}: リプレイ検出用一意 ID
 *   <li>{@code iat}: 発行時刻
 *   <li>{@code challenge} (任意): サーバ提供 challenge
 * </ul>
 */
public class OAuthClientAttestationPop {

  private final String value;

  public OAuthClientAttestationPop() {
    this.value = null;
  }

  public OAuthClientAttestationPop(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isBlank();
  }
}
