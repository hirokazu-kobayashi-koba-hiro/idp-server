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

public class DatadogLogStreamExecutor implements HookExecutor {

  HttpClient httpClient;
  JsonConverter jsonConverter;

  public DatadogLogStreamExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public HookType type() {
    return StandardHookType.DATADOG_LOG.toHookType();
  }

  @Override
  public HookResult execute(
      Tenant tenant,
      HookTriggerType type,
      HookRequest hookRequest,
      HookConfiguration configuration) {

    try {
      WebhookUrl webhookUrl = configuration.webhookUrl();
      WebhookHeaders webhookHeaders = configuration.webhookHeaders();
      WebhookDynamicBodyKeys webhookDynamicBodyKeys = configuration.webhookDynamicBodyKeys();
      WebhookStaticBody webhookStaticBody = configuration.webhookStaticBody();

      validate(webhookHeaders);
      validate(webhookStaticBody);

      WebhookRequestBodyCreator requestBodyCreator =
          new WebhookRequestBodyCreator(hookRequest, webhookDynamicBodyKeys, webhookStaticBody);
      Map<String, Object> requestBodyMap = requestBodyCreator.create();

      String body = jsonConverter.write(requestBodyMap);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(webhookUrl.value()))
              .header("Content-Type", "application/json")
              .header("DD-API-KEY", webhookHeaders.getValueAsString("DD-API-KEY"))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      Map<String, Object> result = new HashMap<>();
      result.put("status", response.statusCode());
      result.put("body", response.body());

      return new HookResult(result);

    } catch (URISyntaxException e) {

      throw new InvalidConfigurationException("Datadog url is invalid.", e);

    } catch (IOException | InterruptedException e) {
      throw new HttpNetworkErrorException("Datadog log stream is failed.", e);
    }
  }

  private void validate(WebhookHeaders webhookHeaders) {
    if (!webhookHeaders.containsKey("DD-API-KEY")) {
      throw new DatadogConfigurationInvalidException("DD-API-KEY header is required.");
    }
  }

  private void validate(WebhookStaticBody webhookStaticBody) {

    if (!webhookStaticBody.containsKey("ddsource")) {
      throw new DatadogConfigurationInvalidException("static body ddsource is required.");
    }
    if (!webhookStaticBody.containsKey("ddtags")) {
      throw new DatadogConfigurationInvalidException("static body ddtags is required.");
    }
    if (!webhookStaticBody.containsKey("ddsource")) {
      throw new DatadogConfigurationInvalidException("static body ddsource is required.");
    }
    if (!webhookStaticBody.containsKey("service")) {
      throw new DatadogConfigurationInvalidException("static body service is required.");
    }
  }
}
