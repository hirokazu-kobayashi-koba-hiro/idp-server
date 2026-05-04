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

package org.idp.server.platform.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 1 件の HTTP リクエストが運んでくる入力チャネルを 1 つにまとめた値オブジェクト。
 *
 * <p>OAuth/OIDC の各エンドポイント (Token / PAR / Authorization / Userinfo / Introspection / Revocation /
 * Resource Server) が共通して必要とする HTTP 由来の入力 — body パラメータ、ヘッダ、TLS 層の証明書、HTTP メソッド/URI — を 1 record
 * に集約する。
 *
 * <p>狙いは以下:
 *
 * <ul>
 *   <li>各 Request DTO で同じ field を再定義する重複を排除
 *   <li>新 HTTP ヘッダ仕様 (例: RFC 9449 DPoP / OAuth-Client-Attestation) の導入時に Controller / DTO
 *       両方を触らずに済む構造にする (Controller が全ヘッダを capture 済みのため、 DTO は accessor を 1 つ生やすだけで良くなる)
 *   <li>protocol 層 (Verifier / Authenticator) からも統一した API でアクセス可能にする
 * </ul>
 *
 * <p>本 record は型を String / Map に絞ってある。プロトコル固有の値オブジェクト ({@code DPoPProof} / {@code ClientCert} /
 * {@code OAuthClientAttestation} 等) への wrap は **呼び出し側 DTO に委ねる**ことで、 platform → core の依存方向を保つ。
 *
 * <p>ヘッダ map の key は **小文字に正規化**する。HTTP ヘッダ名は case-insensitive (RFC 9110 §5.1)。
 *
 * @param authorizationHeader {@code Authorization} ヘッダ生値 (Basic / Bearer / DPoP)。未指定時は {@code null}
 * @param bodyParameters form-urlencoded body の参照 (Spring の {@code MultiValueMap} を {@code
 *     Map<String, String[]>} に変換した結果)。空マップ可、{@code null} 不可
 * @param headers 全 HTTP ヘッダ。key は小文字。同名複数値はリストに格納
 * @param tlsClientCertPem TLS 層 (mTLS) で取り出した PEM 形式のクライアント証明書。未指定時は {@code null}
 * @param httpMethod リクエスト HTTP メソッド (例: {@code "POST"})。DPoP {@code htm} 検証等で参照
 * @param httpUri リクエスト URL の "scheme + authority + path" まで。DPoP {@code htu} 検証等で参照。 query/fragment
 *     は含めない方針 (RFC 9449 §4.3)
 */
public record HttpRequestInputs(
    String authorizationHeader,
    Map<String, String[]> bodyParameters,
    Map<String, List<String>> headers,
    String tlsClientCertPem,
    String httpMethod,
    String httpUri) {

  public HttpRequestInputs {
    bodyParameters = bodyParameters == null ? Map.of() : bodyParameters;
    headers = headers == null ? Map.of() : normalizeHeaders(headers);
  }

  /** 共通空インスタンス。テスト用途や header/body 不要のフローで使う。 */
  public static HttpRequestInputs empty() {
    return new HttpRequestInputs(null, Map.of(), Map.of(), null, "POST", "");
  }

  /**
   * 指定ヘッダの最初の値を返す。
   *
   * @param name ヘッダ名 (大文字小文字不問)
   */
  public Optional<String> firstHeader(String name) {
    List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
    return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
  }

  /**
   * 指定ヘッダの全値を返す。同名複数ヘッダ (例: 複数 DPoP ヘッダ違反検出) を扱う場合に使う。
   *
   * @param name ヘッダ名 (大文字小文字不問)
   */
  public List<String> headerValues(String name) {
    return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
  }

  public boolean hasHeader(String name) {
    return !headerValues(name).isEmpty();
  }

  /**
   * tlsClientCertPem を差し替えた新しい instance を返す (immutable update)。
   *
   * <p>段階移行期間中の旧 setter API を支えるための utility。新コードは canonical constructor を使うこと。
   */
  public HttpRequestInputs withTlsClientCertPem(String pem) {
    return new HttpRequestInputs(
        authorizationHeader, bodyParameters, headers, pem, httpMethod, httpUri);
  }

  /**
   * 指定ヘッダの値を差し替えた新しい instance を返す。{@code values} が null/空 ならそのヘッダを削除する。
   *
   * <p>name は case-insensitive、内部では小文字で保持される。
   */
  public HttpRequestInputs withHeader(String name, List<String> values) {
    Map<String, List<String>> updated = new java.util.HashMap<>(headers);
    String key = name.toLowerCase(Locale.ROOT);
    if (values == null || values.isEmpty()) {
      updated.remove(key);
    } else {
      updated.put(key, java.util.Collections.unmodifiableList(values));
    }
    return new HttpRequestInputs(
        authorizationHeader,
        bodyParameters,
        java.util.Collections.unmodifiableMap(updated),
        tlsClientCertPem,
        httpMethod,
        httpUri);
  }

  /** httpMethod を差し替えた新しい instance を返す。 */
  public HttpRequestInputs withHttpMethod(String method) {
    return new HttpRequestInputs(
        authorizationHeader, bodyParameters, headers, tlsClientCertPem, method, httpUri);
  }

  /** httpUri を差し替えた新しい instance を返す。 */
  public HttpRequestInputs withHttpUri(String uri) {
    return new HttpRequestInputs(
        authorizationHeader, bodyParameters, headers, tlsClientCertPem, httpMethod, uri);
  }

  private static Map<String, List<String>> normalizeHeaders(Map<String, List<String>> raw) {
    Map<String, List<String>> normalized = new LinkedHashMap<>(raw.size());
    for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
      if (entry.getKey() == null) continue;
      List<String> values = entry.getValue();
      normalized.put(
          entry.getKey().toLowerCase(Locale.ROOT),
          values == null ? List.of() : Collections.unmodifiableList(values));
    }
    return Collections.unmodifiableMap(normalized);
  }
}
