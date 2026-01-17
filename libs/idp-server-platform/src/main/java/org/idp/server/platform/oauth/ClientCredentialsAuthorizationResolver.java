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

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.idp.server.platform.http.HttpNetworkErrorException;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.http.SsrfProtectedHttpClient;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;

public class ClientCredentialsAuthorizationResolver implements OAuthAuthorizationResolver {

  SsrfProtectedHttpClient ssrfProtectedHttpClient;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(ClientCredentialsAuthorizationResolver.class);

  public ClientCredentialsAuthorizationResolver(SsrfProtectedHttpClient ssrfProtectedHttpClient) {
    this.ssrfProtectedHttpClient = ssrfProtectedHttpClient;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String type() {
    return "client_credentials";
  }

  @Override
  public String resolve(OAuthAuthorizationConfiguration config) {
    HttpQueryParams httpQueryParams = new HttpQueryParams(config.toRequestValues());

    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(config.tokenEndpoint()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));

    if (config.isClientSecretBasic()) {
      builder.header("Authorization", config.basicAuthenticationValue());
    }

    HttpRequest request = builder.build();

    log.debug("Request headers: {}", request.headers());

    HttpResponse<String> response = ssrfProtectedHttpClient.send(request);

    log.debug("Response status: {}", response.statusCode());
    log.debug("Response body: {}", response.body());

    if (response.statusCode() >= 400 && response.statusCode() < 500) {
      throw new HttpNetworkErrorException(
          "Token request failed: " + response.statusCode(), new RuntimeException(response.body()));
    }

    if (response.statusCode() >= 500) {
      throw new HttpNetworkErrorException(
          "Token request failed: " + response.statusCode(), new RuntimeException(response.body()));
    }

    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(response.body());
    return jsonNodeWrapper.getValueOrEmptyAsString("access_token");
  }
}
