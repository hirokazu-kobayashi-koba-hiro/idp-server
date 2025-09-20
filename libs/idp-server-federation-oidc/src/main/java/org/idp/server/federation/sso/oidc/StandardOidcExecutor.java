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
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.platform.http.HttpClientFactory;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;

public class StandardOidcExecutor implements OidcSsoExecutor {

  LoggerWrapper log = LoggerWrapper.getLogger(StandardOidcExecutor.class);
  HttpClient httpClient;
  JsonConverter jsonConverter;

  public StandardOidcExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SsoProvider type() {
    return new SsoProvider("standard");
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

    try {

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(oidcJwksRequest.endpoint()))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String body = httpResponse.body();
      log.debug("JWKS response: {}", body);

      return new OidcJwksResult(httpResponse.statusCode(), httpResponse.headers().map(), body);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error(e.getMessage(), e);
      return new OidcJwksResult(500, Map.of(), "unexpected network error");
    }
  }

  @Override
  public UserinfoExecutionResult requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest) {
    try {

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(oidcUserinfoRequest.endpoint()))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .header(
                  "Authorization", String.format("Bearer %s", oidcUserinfoRequest.accessToken()))
              .GET()
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String body = httpResponse.body();

      JsonNodeWrapper json = JsonNodeWrapper.fromString(body);
      HashMap<String, Object> map = new HashMap<>();
      map.put("staus_code", httpResponse.statusCode());
      map.put("response_headers", httpResponse.headers());
      map.put("response_body", json.toMap());

      if (httpResponse.statusCode() >= 400 && httpResponse.statusCode() < 500) {
        return UserinfoExecutionResult.clientError(map);
      }

      if (httpResponse.statusCode() >= 500) {
        return UserinfoExecutionResult.serverError(map);
      }

      return UserinfoExecutionResult.success(Map.of("http_request", map));
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error(e.getMessage(), e);
      return UserinfoExecutionResult.serverError(
          Map.of("error", "server_error", "error_description", "unexpected network error"));
    }
  }
}
