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


package org.idp.server.core.oidc.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.idp.server.basic.http.HttpClientFactory;
import org.idp.server.basic.type.oauth.RequestUri;
import org.idp.server.basic.type.oidc.RequestObject;

/** RequestObjectHttpClient */
public class RequestObjectHttpClient implements RequestObjectGateway {

  HttpClient httpClient;

  public RequestObjectHttpClient() {
    this.httpClient = HttpClientFactory.defaultClient();
  }

  @Override
  public RequestObject get(RequestUri requestUri) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(requestUri.value()))
              .GET()
              .header("Content-Type", "application/json")
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = response.body();
      return new RequestObject(body);
    } catch (URISyntaxException | IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
