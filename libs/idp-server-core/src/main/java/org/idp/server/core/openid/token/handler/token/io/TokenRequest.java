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

package org.idp.server.core.openid.token.handler.token.io;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.dpop.DPoPProof;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.openid.token.TokenRequestParameters;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Token endpoint へのリクエストを表現する DTO。
 *
 * <p>HTTP 入力 ({@link HttpRequestInputs}) と endpoint 固有の付随状態 (custom properties 等) を持つ。 各 OAuth/OIDC
 * 仕様 (RFC 6749 / RFC 7521 / RFC 7523 / RFC 9449 / RFC 8705 / FAPI 2.0 等) のリクエストを
 * 受理する正準シグネチャは新コンストラクタ {@link #TokenRequest(Tenant, HttpRequestInputs)} を用いる。
 *
 * <p>旧コンストラクタ ({@code TokenRequest(Tenant, String, Map)}) と setter 群は段階移行のために残しているが、
 * 新コードは新シグネチャを使うこと。
 */
public class TokenRequest implements AuthorizationHeaderHandlerable {

  private final Tenant tenant;
  private HttpRequestInputs http;
  private Map<String, Object> customProperties = new HashMap<>();

  /**
   * Canonical constructor.
   *
   * @param tenant 対象テナント
   * @param http HTTP リクエストの入力チャネル ({@code Authorization} / body / headers / mTLS / method / URI)
   */
  public TokenRequest(Tenant tenant, HttpRequestInputs http) {
    this.tenant = tenant;
    this.http = http == null ? HttpRequestInputs.empty() : http;
  }

  /**
   * 旧コンストラクタ。後方互換のために維持しているが、新規コードでは {@link #TokenRequest(Tenant, HttpRequestInputs)} を使うこと。 setter
   * 経由で {@code clientCert} / {@code dpopProofHeaders} / {@code httpMethod} / {@code httpUri}
   * を埋めるパターン。
   */
  @Deprecated
  public TokenRequest(Tenant tenant, String authorizationHeaders, Map<String, String[]> params) {
    this.tenant = tenant;
    this.http = new HttpRequestInputs(authorizationHeaders, params, Map.of(), null, "POST", "");
  }

  // ---------------------------------------------------------------------------
  // Deprecated mutators — kept for backward compatibility during migration.
  // ---------------------------------------------------------------------------

  @Deprecated
  public TokenRequest setClientCert(String clientCert) {
    this.http = http.withTlsClientCertPem(clientCert);
    return this;
  }

  @Deprecated
  public TokenRequest setDPoPProofHeaders(List<String> dpopProofHeaders) {
    this.http = http.withHeader("DPoP", dpopProofHeaders);
    return this;
  }

  @Deprecated
  public TokenRequest setHttpMethod(String httpMethod) {
    this.http = http.withHttpMethod(httpMethod);
    return this;
  }

  @Deprecated
  public TokenRequest setHttpUri(String httpUri) {
    this.http = http.withHttpUri(httpUri);
    return this;
  }

  // ---------------------------------------------------------------------------
  // Accessors — protocol layer reads from these.
  // ---------------------------------------------------------------------------

  public HttpRequestInputs http() {
    return http;
  }

  public List<String> dpopProofHeaders() {
    return http.headerValues("DPoP");
  }

  public DPoPProof toDPoPProof() {
    List<String> values = dpopProofHeaders();
    if (values.isEmpty()) {
      return new DPoPProof();
    }
    return new DPoPProof(values.get(0));
  }

  public String httpMethod() {
    String method = http.httpMethod();
    return method != null && !method.isEmpty() ? method : "POST";
  }

  public String httpUri() {
    String uri = http.httpUri();
    return uri != null ? uri : "";
  }

  public String getAuthorizationHeaders() {
    return http.authorizationHeader();
  }

  public Map<String, String[]> getParams() {
    return http.bodyParameters();
  }

  public String getClientCert() {
    return http.tlsClientCertPem();
  }

  public Map<String, Object> customProperties() {
    return customProperties;
  }

  public TokenRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  /**
   * Extracts the client_id from the request.
   *
   * <p>Per RFC 7521 Section 4.2, when using JWT-based client authentication (private_key_jwt or
   * client_secret_jwt), the client_id parameter is OPTIONAL because the client can be identified by
   * the issuer (iss) claim within the client_assertion JWT.
   *
   * <p>The extraction follows this priority order:
   *
   * <ol>
   *   <li>Explicit client_id parameter in request body
   *   <li>HTTP Basic Authentication header (username)
   *   <li>Issuer (iss) claim from client_assertion JWT (RFC 7523)
   * </ol>
   *
   * @return the extracted client_id, or empty if not found
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7521#section-4.2">RFC 7521 Section
   *     4.2</a>
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523#section-3">RFC 7523 Section 3</a>
   */
  public RequestedClientId clientId() {
    TokenRequestParameters parameters = toParameters();
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    String authorizationHeaders = http.authorizationHeader();
    if (isBasicAuth(authorizationHeaders)) {
      BasicAuth basicAuth = convertBasicAuth(authorizationHeaders);
      return new RequestedClientId(basicAuth.username());
    }
    // RFC 7521/7523: Extract client_id from client_assertion JWT's iss claim
    if (parameters.hasClientAssertion()) {
      String issuer = parameters.clientAssertion().extractIssuer();
      if (!issuer.isEmpty()) {
        return new RequestedClientId(issuer);
      }
    }
    return new RequestedClientId();
  }

  public ClientSecretBasic clientSecretBasic() {
    String authorizationHeaders = http.authorizationHeader();
    if (isBasicAuth(authorizationHeaders)) {
      return new ClientSecretBasic(convertBasicAuth(authorizationHeaders));
    }
    return new ClientSecretBasic();
  }

  public Tenant tenant() {
    return tenant;
  }

  public TokenRequestParameters toParameters() {
    return new TokenRequestParameters(http.bodyParameters());
  }

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }

  public ClientCert toClientCert() {
    return new ClientCert(http.tlsClientCertPem());
  }

  public boolean isRefreshTokenGrant() {
    return toParameters().isRefreshTokenGrant();
  }
}
