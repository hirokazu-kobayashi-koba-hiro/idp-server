package org.idp.server.core.adapters.hook;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.http.HttpClientFactory;
import org.idp.server.core.basic.http.HttpNetworkErrorException;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.hook.*;
import org.idp.server.core.hook.webhook.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.exception.InvalidConfigurationException;

public class WebHookExecutor implements HookExecutor {

  HttpClient httpClient;
  JsonConverter jsonConverter;

  public WebHookExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public HookResult execute(
      Tenant tenant,
      HookTriggerType type,
      HookRequest hookRequest,
      HookConfiguration configuration) {

    try {
      WebhookUrl webhookUrl = configuration.webhookUrl();
      WebhookMethod webhookMethod = configuration.webhookMethod();
      WebhookHeaders webhookHeaders = configuration.webhookHeaders();
      WebhookParameters webhookParameters = configuration.webhookParameters();

      WebhookRequestBodyCreator requestBodyCreator =
          new WebhookRequestBodyCreator(hookRequest, webhookParameters);
      Map<String, Object> requestBody = requestBodyCreator.create();

      HttpRequest.Builder httpRequestBuilder =
          HttpRequest.newBuilder()
              .uri(new URI(webhookUrl.value()))
              .header("Content-Type", "application/json");

      setHeaders(httpRequestBuilder, webhookHeaders);
      setParams(httpRequestBuilder, webhookMethod, requestBody);

      HttpRequest httpRequest = httpRequestBuilder.build();

      HttpResponse<String> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      Map<String, Object> result = new HashMap<>();
      result.put("status", httpResponse.statusCode());
      result.put("body", httpResponse.body());

      return new HookResult(result);
    } catch (URISyntaxException e) {

      throw new InvalidConfigurationException("WebhookUrl is invalid.", e);

    } catch (IOException | InterruptedException e) {
      throw new HttpNetworkErrorException("Webhook request is failed.", e);
    }
  }

  private void setHeaders(HttpRequest.Builder httpRequestBuilder, WebhookHeaders webhookHeaders) {
    webhookHeaders.forEach(httpRequestBuilder::header);
  }

  private void setParams(
      HttpRequest.Builder builder, WebhookMethod webhookMethod, Map<String, Object> requestBody) {

    switch (webhookMethod) {
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
