package org.idp.server.security.event.hooks.webhook;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.http.*;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventHookExecutor;
import org.idp.server.platform.security.hook.*;

/**
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8935">Push-Based Security Event Token
 *     (SET) Delivery Using HTTP</a>
 */
public class WebHookExecutor implements SecurityEventHookExecutor {

  HttpClient httpClient;
  JsonConverter jsonConverter;

  public WebHookExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.WEBHOOK.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    try {

      WebHookConfiguration configuration =
          jsonConverter.read(hookConfiguration, WebHookConfiguration.class);
      HttpRequestUrl httpRequestUrl = configuration.httpRequestUrl(securityEvent.type());
      HttpMethod httpMethod = configuration.httpMethod(securityEvent.type());
      HttpRequestHeaders httpRequestHeaders =
          configuration.httpRequestHeaders(securityEvent.type());
      HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys =
          configuration.httpRequestDynamicBodyKeys(securityEvent.type());
      HttpRequestStaticBody httpRequestStaticBody =
          configuration.httpRequestStaticBody(securityEvent.type());

      HttpRequestBodyCreator requestBodyCreator =
          new HttpRequestBodyCreator(
              new HttpRequestBaseParams(securityEvent.toMap()),
              httpRequestDynamicBodyKeys,
              httpRequestStaticBody);
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

      Map<String, Object> result = new HashMap<>();
      result.put("status", httpResponse.statusCode());
      result.put("body", httpResponse.body());

      return SecurityEventHookResult.success(type(), result);
    } catch (URISyntaxException e) {

      Map<String, Object> response = new HashMap<>();
      response.put("message", "WebhookUrl is invalid.");
      return SecurityEventHookResult.failure(type(), response);

    } catch (IOException | InterruptedException e) {

      Map<String, Object> response = new HashMap<>();
      response.put("message", "Webhook request is failed." + e.getMessage());
      return SecurityEventHookResult.failure(type(), response);
    } catch (Exception e) {

      Map<String, Object> response = new HashMap<>();
      response.put("message", "Unexpected error. Webhook request is failed." + e.getMessage());
      return SecurityEventHookResult.failure(type(), response);
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
