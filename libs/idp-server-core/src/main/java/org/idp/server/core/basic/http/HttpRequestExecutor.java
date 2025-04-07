package org.idp.server.core.basic.http;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.type.exception.InvalidConfigurationException;

public class HttpRequestExecutor {

  HttpClient httpClient;
  JsonConverter jsonConverter;

  public HttpRequestExecutor(HttpClient httpClient) {
    this.httpClient = httpClient;
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

      HttpRequest.Builder httpRequestBuilder =
          HttpRequest.newBuilder()
              .uri(new URI(httpRequestUrl.value()))
              .header("Content-Type", "application/json");

      setHeaders(httpRequestBuilder, httpRequestHeaders);
      setParams(httpRequestBuilder, httpMethod, requestBody);

      HttpRequest httpRequest = httpRequestBuilder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      JsonNodeWrapper jsonResponse = jsonConverter.readTree(httpResponse.body());

      return new HttpRequestResult(
          httpResponse.statusCode(), httpResponse.headers().map(), jsonResponse);
    } catch (URISyntaxException e) {

      throw new InvalidConfigurationException("HttpRequestUrl is invalid.", e);

    } catch (IOException | InterruptedException e) {
      throw new HttpNetworkErrorException("Http request is failed.", e);
    }
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
