package org.idp.server.core.adapters.security.hook;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.http.*;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventHookExecutor;
import org.idp.server.core.security.hook.*;
import org.idp.server.core.security.hook.webhook.WebHookConfiguration;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.exception.InvalidConfigurationException;

public class DatadogLogStreamExecutorSecurityEvent implements SecurityEventHookExecutor {

  HttpClient httpClient;
  JsonConverter jsonConverter;

  public DatadogLogStreamExecutorSecurityEvent() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public SecurityEventHookType type() {
    return new SecurityEventHookType("DATADOG_LOG");
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
      HttpRequestHeaders httpRequestHeaders =
          configuration.httpRequestHeaders(securityEvent.type());
      HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys =
          configuration.httpRequestDynamicBodyKeys(securityEvent.type());
      HttpRequestStaticBody httpRequestStaticBody =
          configuration.httpRequestStaticBody(securityEvent.type());

      validate(httpRequestHeaders);
      validate(httpRequestStaticBody);

      HttpRequestBodyCreator requestBodyCreator =
          new HttpRequestBodyCreator(
              new HttpRequestBaseParams(securityEvent.toMap()),
              httpRequestDynamicBodyKeys,
              httpRequestStaticBody);
      Map<String, Object> requestBodyMap = requestBodyCreator.create();

      String body = jsonConverter.write(requestBodyMap);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(httpRequestUrl.value()))
              .header("Content-Type", "application/json")
              .header("DD-API-KEY", httpRequestHeaders.getValueAsString("DD-API-KEY"))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      Map<String, Object> result = new HashMap<>();
      result.put("status", response.statusCode());
      result.put("body", response.body());

      return new SecurityEventHookResult(result);

    } catch (URISyntaxException e) {

      throw new InvalidConfigurationException("Datadog url is invalid.", e);

    } catch (IOException | InterruptedException e) {
      throw new HttpNetworkErrorException("Datadog log stream is failed.", e);
    }
  }

  private void validate(HttpRequestHeaders httpRequestHeaders) {
    if (!httpRequestHeaders.containsKey("DD-API-KEY")) {
      throw new DatadogConfigurationInvalidException("DD-API-KEY header is required.");
    }
  }

  private void validate(HttpRequestStaticBody httpRequestStaticBody) {

    if (!httpRequestStaticBody.containsKey("ddsource")) {
      throw new DatadogConfigurationInvalidException("static body ddsource is required.");
    }
    if (!httpRequestStaticBody.containsKey("ddtags")) {
      throw new DatadogConfigurationInvalidException("static body ddtags is required.");
    }
    if (!httpRequestStaticBody.containsKey("ddsource")) {
      throw new DatadogConfigurationInvalidException("static body ddsource is required.");
    }
    if (!httpRequestStaticBody.containsKey("service")) {
      throw new DatadogConfigurationInvalidException("static body service is required.");
    }
  }
}
