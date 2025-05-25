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

package org.idp.server.platform.oauth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.idp.server.platform.http.HttpClientFactory;
import org.idp.server.platform.http.HttpNetworkErrorException;
import org.idp.server.platform.http.QueryParams;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;

public class ClientCredentialsAuthorizationResolver implements OAuthAuthorizationResolver {

  HttpClient httpClient;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(ClientCredentialsAuthorizationResolver.class);

  public ClientCredentialsAuthorizationResolver() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String resolve(OAuthAuthorizationConfiguration config) {
    try {

      QueryParams queryParams = new QueryParams(config.toRequestValues());

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(config.tokenEndpoint()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(queryParams.params()));

      if (config.isClientSecretBasic()) {
        builder.header("Authorization", config.basicAuthenticationValue());
      }

      HttpRequest request = builder.build();

      log.debug("Request headers: {}", request.headers());
      if (request.bodyPublisher().isPresent()) {
        log.debug("Request body: {}", request.bodyPublisher().get());
      }

      HttpResponse<String> httpResponse =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = httpResponse.body();

      log.debug("Response status: {}", httpResponse.statusCode());
      log.debug("Response body: {}", httpResponse.body());

      JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(body);

      String accessToken = jsonNodeWrapper.getValueOrEmptyAsString("access_token");

      return accessToken;
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error(e.getMessage(), e);
      throw new HttpNetworkErrorException("unexpected network error", e);
    }
  }
}
