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

package org.idp.server.account_linking.gateway;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.federation.sso.oidc.OidcTokenResult;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Token endpoint calls for the linking flow.
 *
 * <p>Separate from {@code OidcSsoExecutor#requestToken} because that path cannot carry a {@code
 * code_verifier}: {@code OidcTokenRequest} has no field for one. Refresh lives here too, so that
 * both the lazy refresh and the explicit refresh endpoint go through one implementation.
 */
public class ExternalIdpTokenGateway {

  HttpRequestExecutor httpRequestExecutor;
  LoggerWrapper log = LoggerWrapper.getLogger(ExternalIdpTokenGateway.class);

  public ExternalIdpTokenGateway(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
  }

  public OidcTokenResult exchangeAuthorizationCode(
      OidcSsoConfiguration configuration, String code, String redirectUri, String codeVerifier) {
    Map<String, String> params = new HashMap<>();
    params.put("grant_type", "authorization_code");
    params.put("code", code);
    params.put("redirect_uri", redirectUri);
    params.put("code_verifier", codeVerifier);

    return request(configuration, params);
  }

  public OidcTokenResult refresh(OidcSsoConfiguration configuration, String refreshToken) {
    Map<String, String> params = new HashMap<>();
    params.put("grant_type", "refresh_token");
    params.put("refresh_token", refreshToken);

    return request(configuration, params);
  }

  private OidcTokenResult request(OidcSsoConfiguration configuration, Map<String, String> params) {
    try {
      boolean clientSecretBasic = isClientSecretBasic(configuration);

      params.put("client_id", configuration.clientId());
      if (!clientSecretBasic) {
        params.put("client_secret", configuration.clientSecret());
      }

      HttpQueryParams httpQueryParams = new HttpQueryParams(params);
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(configuration.tokenEndpoint()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));

      if (clientSecretBasic) {
        builder.header("Authorization", basicAuthenticationValue(configuration));
      }

      HttpRequestResult httpResult = httpRequestExecutor.execute(builder.build());

      return new OidcTokenResult(
          httpResult.statusCode(), httpResult.headers(), httpResult.body().toMap());
    } catch (Exception e) {
      log.error("Account linking token request failed: {}", e.getMessage(), e);
      return new OidcTokenResult(
          500,
          Map.of(),
          Map.of("error", "server_error", "error_description", "unexpected network error"));
    }
  }

  private boolean isClientSecretBasic(OidcSsoConfiguration configuration) {
    return Objects.equals(configuration.clientAuthenticationType(), "client_secret_basic");
  }

  private String basicAuthenticationValue(OidcSsoConfiguration configuration) {
    String auth = configuration.clientId() + ":" + configuration.clientSecret();
    return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
  }
}
