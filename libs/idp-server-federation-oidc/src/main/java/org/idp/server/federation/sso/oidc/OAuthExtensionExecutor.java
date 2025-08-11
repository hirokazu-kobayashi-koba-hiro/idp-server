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

package org.idp.server.federation.sso.oidc;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.core.openid.federation.sso.oidc.OidcSsoSession;
import org.idp.server.platform.http.HttpClientFactory;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;

public class OAuthExtensionExecutor implements OidcSsoExecutor {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthExtensionExecutor.class);
  HttpClient httpClient;
  UserinfoExecutors userinfoExecutors;
  JsonConverter jsonConverter;

  public OAuthExtensionExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.userinfoExecutors = new UserinfoExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SsoProvider type() {
    return new SsoProvider("oauth-extension");
  }

  @Override
  public OidcTokenResult requestToken(OidcTokenRequest oidcTokenRequest) {
    try {

      HttpQueryParams httpQueryParams = new HttpQueryParams(oidcTokenRequest.toMap());

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(oidcTokenRequest.endpoint()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));

      if (oidcTokenRequest.isClientSecretBasic()) {
        builder.header("Authorization", oidcTokenRequest.basicAuthenticationValue());
      }

      HttpRequest request = builder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = httpResponse.body();

      JsonNodeWrapper json = JsonNodeWrapper.fromString(body);

      return new OidcTokenResult(
          httpResponse.statusCode(), httpResponse.headers().map(), json.toMap());
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error(e.getMessage(), e);
      return new OidcTokenResult(
          500,
          Map.of(),
          Map.of("error", "server_error", "error_description", "unexpected network error"));
    }
  }

  @Override
  public OidcJwksResult getJwks(OidcJwksRequest oidcJwksRequest) {
    return new OidcJwksResult(200, Map.of(), null);
  }

  @Override
  public IdTokenVerificationResult verifyIdToken(
      OidcSsoConfiguration configuration,
      OidcSsoSession ssoSession,
      OidcJwksResult jwksResponse,
      OidcTokenResult tokenResponse) {

    return new IdTokenVerificationResult(true, Map.of());
  }

  @Override
  public UserinfoExecutionResult requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest) {

    OAuthExtensionUserinfoExecutionConfig execution = oidcUserinfoRequest.userinfoExecution();
    UserinfoExecutor executor = userinfoExecutors.get(execution.function());
    UserinfoExecutionRequest executionRequest =
        new UserinfoExecutionRequest(Map.of("access_token", oidcUserinfoRequest.accessToken()));

    return executor.execute(executionRequest, execution);
  }
}
