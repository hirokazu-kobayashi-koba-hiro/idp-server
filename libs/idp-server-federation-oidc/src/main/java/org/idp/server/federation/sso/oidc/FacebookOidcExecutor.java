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
import org.idp.server.basic.http.HttpClientErrorException;
import org.idp.server.basic.http.HttpClientFactory;
import org.idp.server.basic.http.HttpNetworkErrorException;
import org.idp.server.basic.http.QueryParams;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.platform.log.LoggerWrapper;

public class FacebookOidcExecutor implements OidcSsoExecutor {

  LoggerWrapper log = LoggerWrapper.getLogger(FacebookOidcExecutor.class);
  HttpClient httpClient;
  JsonConverter jsonConverter;

  public FacebookOidcExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SsoProvider type() {
    return SupportedOidcProvider.Facebook.toSsoProvider();
  }

  @Override
  public OidcTokenResponse requestToken(OidcTokenRequest oidcTokenRequest) {
    try {

      QueryParams queryParams = new QueryParams(oidcTokenRequest.toMap());

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(oidcTokenRequest.endpoint()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(queryParams.params()));

      if (oidcTokenRequest.isClientSecretBasic()) {
        builder.header("Authorization", oidcTokenRequest.basicAuthenticationValue());
      }

      HttpRequest request = builder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = httpResponse.body();

      validateResponse(httpResponse, body);

      Map map = jsonConverter.read(body, Map.class);

      return new OidcTokenResponse(map);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }

  @Override
  public OidcJwksResponse getJwks(OidcJwksRequest oidcJwksRequest) {

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
      log.info("jwks response:" + body);

      validateResponse(httpResponse, body);

      return new OidcJwksResponse(body);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }

  @Override
  public OidcUserinfoResponse requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest) {
    try {

      QueryParams queryParams = new QueryParams();
      queryParams.add("fields", "id,name,email,picture");
      queryParams.add("access_token", oidcUserinfoRequest.accessToken());
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(oidcUserinfoRequest.endpoint() + "?" + queryParams.params()))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String body = httpResponse.body();

      validateResponse(httpResponse, body);

      Map map = jsonConverter.read(body, Map.class);

      return new OidcUserinfoResponse(map);
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error(e.getMessage());
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }

  private void validateResponse(HttpResponse<String> httpResponse, String body) {
    if (httpResponse.statusCode() >= 400 && httpResponse.statusCode() < 500) {
      throw new HttpClientErrorException(body, httpResponse.statusCode());
    }

    if (httpResponse.statusCode() >= 500) {
      throw new HttpClientErrorException(body, httpResponse.statusCode());
    }
  }
}
