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

package org.idp.server.core.openid.extension.fapi;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;

/**
 * FAPI 2.0 verifier に渡る検証コンテキスト。
 *
 * <p>{@link OAuthRequestContext} (リクエスト + サーバ/クライアント設定) と、PAR エンドポイントで利用可能な {@link
 * ClientCredentials} (=parsed client_assertion 等) を 1 つにまとめた値オブジェクト。
 *
 * <p>{@code credentials} は authorization endpoint からの呼び出し時 (client_assertion がまだ無い段階) に {@code
 * null} となる。 client_assertion を見るルールは {@code appliesTo} で {@code credentials != null} を確認する。
 *
 * @param request OAuth/OIDC リクエスト 1 件分のコンテキスト (必須)
 * @param credentials parsed client credentials (PAR 時のみ存在、それ以外は null)
 */
public record Fapi20Context(OAuthRequestContext request, ClientCredentials credentials) {

  public boolean hasCredentials() {
    return credentials != null;
  }
}
