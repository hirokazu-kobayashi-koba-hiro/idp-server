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

package org.idp.server.core.openid.oauth.gateway;

import java.net.URI;
import java.net.http.HttpRequest;
import org.idp.server.core.openid.oauth.type.oauth.RequestUri;
import org.idp.server.core.openid.oauth.type.oidc.RequestObject;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;

/** RequestObjectHttpClient */
public class RequestObjectHttpClient implements RequestObjectGateway {

  HttpRequestExecutor httpRequestExecutor;

  public RequestObjectHttpClient(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
  }

  @Override
  public RequestObject get(RequestUri requestUri) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(requestUri.value()))
              .GET()
              .header("Content-Type", "application/json")
              .build();

      HttpRequestResult result = httpRequestExecutor.execute(request);

      if (result.isClientError() || result.isServerError()) {
        throw new RuntimeException("Failed to fetch request object: " + result.statusCode());
      }

      return new RequestObject(result.body().toString());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
