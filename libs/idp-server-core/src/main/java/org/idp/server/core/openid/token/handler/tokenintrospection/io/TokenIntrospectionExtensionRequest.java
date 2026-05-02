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

package org.idp.server.core.openid.token.handler.tokenintrospection.io;

import java.util.Map;
import org.idp.server.core.openid.oauth.dpop.DPoPProof;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Request for the {@code /v1/tokens/introspection-extensions} endpoint (Resource Server forwarding
 * pattern).
 *
 * <p>Unlike {@code /v1/tokens/introspection} (RFC 7662, basic introspection), this endpoint is
 * designed for a Resource Server (RS) to verify the sender-constraint of an access token (RFC 8705
 * mTLS or RFC 9449 DPoP) on behalf of the request it just received from the Client. To do so the RS
 * must hand the AS the artifacts the Client presented at the RS's resource endpoint:
 *
 * <ul>
 *   <li>{@code client_cert} (body): the PEM-encoded client certificate the Client used at the RS.
 *       Used for token-binding verification (RFC 8705 §3).
 *   <li>{@code dpop_proof} (body): the DPoP proof JWT the Client used at the RS. Used for binding
 *       verification (RFC 9449 §7).
 *   <li>{@code dpop_htm} / {@code dpop_htu} (body): the HTTP method / URI the Client used at the
 *       RS, against which the DPoP proof's {@code htm} / {@code htu} claims must match.
 * </ul>
 *
 * <p>The TLS-layer {@code x-ssl-cert} header is the RS's own client certificate used to
 * authenticate to the AS, which is independent from the token-binding certificate.
 */
public class TokenIntrospectionExtensionRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  String authorizationHeaders;
  Map<String, String[]> params;
  String clientCert;

  public TokenIntrospectionExtensionRequest(
      Tenant tenant, String authorizationHeaders, Map<String, String[]> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public TokenIntrospectionExtensionRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public String getAuthorizationHeaders() {
    return authorizationHeaders;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String getClientCert() {
    return clientCert;
  }

  public TokenIntrospectionRequestParameters toParameters() {
    return new TokenIntrospectionRequestParameters(params);
  }

  public Tenant tenant() {
    return tenant;
  }

  public RequestedClientId clientId() {
    TokenIntrospectionRequestParameters parameters = toParameters();

    if (isBasicAuth(authorizationHeaders)) {
      BasicAuth basicAuth = convertBasicAuth(authorizationHeaders);
      return new RequestedClientId(basicAuth.username());
    }

    if (parameters.hasClientId()) {
      return parameters.clientId();
    }

    return new RequestedClientId();
  }

  public ClientSecretBasic clientSecretBasic() {
    if (isBasicAuth(authorizationHeaders)) {
      return new ClientSecretBasic(convertBasicAuth(authorizationHeaders));
    }
    return new ClientSecretBasic();
  }

  public String token() {
    if (hasToken()) {
      return params.get("token")[0];
    }
    return "";
  }

  public boolean hasToken() {
    return params.containsKey("token");
  }

  public Scopes scopes() {

    if (hasScope()) {
      String scopes = params.get("scope")[0];

      return new Scopes(scopes);
    }
    return new Scopes();
  }

  public boolean hasScope() {
    return params.containsKey("scope");
  }

  public ClientCert clientCertForTokenBinding() {

    if (hasClientCertForTokenBinding()) {
      String clientCert = params.get("client_cert")[0];
      return new ClientCert(clientCert);
    }

    return new ClientCert();
  }

  public boolean hasClientCertForTokenBinding() {
    return params.containsKey("client_cert");
  }

  public ClientCert clientCertFormMtls() {
    return new ClientCert(clientCert);
  }

  /**
   * Returns the DPoP proof JWT the Client used at the Resource Server, forwarded to the AS via the
   * {@code dpop_proof} body parameter (RS forwarding pattern, mirror of {@code client_cert}).
   *
   * <p>This endpoint intentionally does NOT consult the {@code DPoP} HTTP header — the header value
   * would be the RS's own DPoP proof for the introspection call (with {@code htu} pointing at the
   * introspection endpoint), which is not what we want to verify here.
   */
  public DPoPProof dpopProof() {
    if (!params.containsKey("dpop_proof")) {
      return new DPoPProof();
    }
    String value = params.get("dpop_proof")[0];
    if (value == null || value.isEmpty()) {
      return new DPoPProof();
    }
    return new DPoPProof(value);
  }

  /**
   * Returns the HTTP method the Client used at the Resource Server, forwarded via the {@code
   * dpop_htm} body parameter, against which the DPoP proof's {@code htm} claim must match. Defaults
   * to {@code POST} when not provided (typical for fresh proofs).
   */
  public String httpMethod() {
    if (params.containsKey("dpop_htm")) {
      String value = params.get("dpop_htm")[0];
      if (value != null && !value.isEmpty()) {
        return value;
      }
    }
    return "POST";
  }

  /**
   * Returns the URL the Client used at the Resource Server, forwarded via the {@code dpop_htu} body
   * parameter, against which the DPoP proof's {@code htu} claim must match. Empty string when not
   * provided.
   */
  public String httpUri() {
    if (params.containsKey("dpop_htu")) {
      String value = params.get("dpop_htu")[0];
      if (value != null && !value.isEmpty()) {
        return value;
      }
    }
    return "";
  }
}
