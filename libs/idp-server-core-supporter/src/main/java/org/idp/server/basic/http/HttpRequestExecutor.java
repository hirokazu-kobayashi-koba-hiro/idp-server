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

package org.idp.server.basic.http;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.platform.exception.InvalidConfigurationException;
import org.idp.server.platform.log.LoggerWrapper;

public class HttpRequestExecutor {

  HttpClient httpClient;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestExecutor.class);

  public HttpRequestExecutor(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public HttpRequestResult execute(
      HttpRequestUrl httpRequestUrl,
      HttpMethod httpMethod,
      HttpRequestHeaders httpRequestHeaders,
      HttpRequestBaseParams httpRequestBaseParams,
      HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys,
      HttpRequestStaticBody httpRequestStaticBody) {

    try {

      HttpRequestBodyCreator requestBodyCreator =
          new HttpRequestBodyCreator(
              httpRequestBaseParams, httpRequestDynamicBodyKeys, httpRequestStaticBody);
      Map<String, Object> requestBody = requestBodyCreator.create();

      log.debug("Request headers: {}", httpRequestHeaders);
      log.debug("Request body: {}", requestBody);

      HttpRequest.Builder httpRequestBuilder =
          HttpRequest.newBuilder()
              .uri(new URI(httpRequestUrl.value()))
              .header("Content-Type", "application/json");

      setHeaders(httpRequestBuilder, httpRequestHeaders);
      setParams(httpRequestBuilder, httpMethod, requestBody);

      HttpRequest httpRequest = httpRequestBuilder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      log.debug("Response status: {}", httpResponse.statusCode());
      log.debug("Response body: {}", httpResponse.body());

      JsonNodeWrapper jsonResponse = resolveResponseBody(httpResponse);

      return new HttpRequestResult(
          httpResponse.statusCode(), httpResponse.headers().map(), jsonResponse);
    } catch (URISyntaxException e) {

      throw new InvalidConfigurationException("HttpRequestUrl is invalid.", e);

    } catch (IOException | InterruptedException e) {
      throw new HttpNetworkErrorException("Http request is failed.", e);
    }
  }

  private JsonNodeWrapper resolveResponseBody(HttpResponse<String> httpResponse) {
    if (httpResponse.body() == null || httpResponse.body().isEmpty()) {
      return JsonNodeWrapper.empty();
    }

    return jsonConverter.readTree(httpResponse.body());
  }

  private void setHeaders(
      HttpRequest.Builder httpRequestBuilder, HttpRequestHeaders httpRequestHeaders) {
    httpRequestHeaders.forEach(httpRequestBuilder::header);
  }

  private void setParams(
      HttpRequest.Builder builder, HttpMethod httpMethod, Map<String, Object> requestBody) {

    switch (httpMethod) {
      case GET:
        builder.GET();
        break;
      case POST:
        {
          builder.POST(HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          break;
        }
      case PUT:
        {
          builder.PUT(HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          break;
        }
      case DELETE:
        {
          builder.DELETE();
          break;
        }
    }
  }
}
