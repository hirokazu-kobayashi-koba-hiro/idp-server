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

package org.idp.server.core.openid.oauth.io;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.request.OAuthPushedRequestParameters;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * PAR (Pushed Authorization Request, RFC 9126) endpoint へのリクエストを表現する DTO。
 *
 * <p>HTTP 入力 ({@link HttpRequestInputs}) と endpoint 固有の付随状態を持つ。 正準シグネチャは新コンストラクタ {@link
 * #OAuthPushedRequest(Tenant, HttpRequestInputs)} を用いる。
 *
 * <p>旧コンストラクタと setter 群は段階移行のために残しているが、新コードは新シグネチャを使うこと。
 */
public class OAuthPushedRequest implements AuthorizationHeaderHandlerable {

  private final Tenant tenant;
  private HttpRequestInputs http;

  /**
   * Canonical constructor.
   *
   * @param tenant 対象テナント
   * @param http HTTP リクエストの入力チャネル ({@code Authorization} / body / headers / mTLS / method / URI)
   */
  public OAuthPushedRequest(Tenant tenant, HttpRequestInputs http) {
    this.tenant = tenant;
    this.http = http == null ? HttpRequestInputs.empty() : http;
  }

  /**
   * 旧コンストラクタ。後方互換のために維持しているが、新規コードでは {@link #OAuthPushedRequest(Tenant, HttpRequestInputs)} を使うこと。
   */
  @Deprecated
  public OAuthPushedRequest(
      Tenant tenant, String authorizationHeaders, Map<String, String[]> params) {
    this.tenant = tenant;
    this.http = new HttpRequestInputs(authorizationHeaders, params, Map.of(), null, "POST", "");
  }

  // ---------------------------------------------------------------------------
  // Deprecated mutators — kept for backward compatibility during migration.
  // ---------------------------------------------------------------------------

  @Deprecated
  public OAuthPushedRequest setClientCert(String clientCert) {
    this.http = http.withTlsClientCertPem(clientCert);
    return this;
  }

  @Deprecated
  public OAuthPushedRequest setDPoPProofHeaders(List<String> dpopProofHeaders) {
    this.http = http.withHeader("DPoP", dpopProofHeaders);
    return this;
  }

  @Deprecated
  public OAuthPushedRequest setHttpMethod(String httpMethod) {
    this.http = http.withHttpMethod(httpMethod);
    return this;
  }

  @Deprecated
  public OAuthPushedRequest setHttpUri(String httpUri) {
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

  public String httpMethod() {
    String method = http.httpMethod();
    return method != null && !method.isEmpty() ? method : "POST";
  }

  public String httpUri() {
    String uri = http.httpUri();
    return uri != null ? uri : "";
  }

  public Map<String, String[]> getParams() {
    return http.bodyParameters();
  }

  public String clientCert() {
    return http.tlsClientCertPem();
  }

  public Tenant tenant() {
    return tenant;
  }

  public OAuthPushedRequestParameters toBackchannelParameters() {
    return new OAuthPushedRequestParameters(http.bodyParameters());
  }

  public OAuthRequestParameters toOAuthRequestParameters() {
    return new OAuthRequestParameters(http.bodyParameters());
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
   */
  public RequestedClientId clientId() {
    OAuthPushedRequestParameters parameters = toBackchannelParameters();
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

  public boolean hasClientId() {
    return clientId().exists();
  }

  public ClientSecretBasic clientSecretBasic() {
    String authorizationHeaders = http.authorizationHeader();
    if (isBasicAuth(authorizationHeaders)) {
      return new ClientSecretBasic(convertBasicAuth(authorizationHeaders));
    }
    return new ClientSecretBasic();
  }

  public ClientCert toClientCert() {
    return new ClientCert(http.tlsClientCertPem());
  }
}
