package org.idp.server.core.adapters.security.hook;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.exception.InvalidConfigurationException;
import org.idp.server.basic.http.HttpClientFactory;
import org.idp.server.basic.http.HttpNetworkErrorException;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.notification.NotificationTemplateInterpolator;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventHookExecutor;
import org.idp.server.core.security.hook.*;

public class SlacklNotificationSecurityEventHookExecutor implements SecurityEventHookExecutor {

  HttpClient httpClient;
  JsonConverter jsonConverter;

  public SlacklNotificationSecurityEventHookExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SecurityEventHookType type() {
    return new SecurityEventHookType("SLACK");
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    SlackSecurityEventHookConfiguration configuration =
        jsonConverter.read(hookConfiguration.details(), SlackSecurityEventHookConfiguration.class);
    String incomingWebhookUrl = configuration.incomingWebhookUrl(securityEvent.type());
    if (incomingWebhookUrl == null) {
      return new SecurityEventHookResult(Map.of("status", 500, "error", "invalid_configuration"));
    }

    String template = configuration.messageTemplate(securityEvent.type());

    Map<String, Object> context = new HashMap<>(securityEvent.toMap());
    context.put("trigger", securityEvent.type().value());

    NotificationTemplateInterpolator notificationTemplateInterpolator =
        new NotificationTemplateInterpolator(template, context);
    String message = notificationTemplateInterpolator.interpolate();

    String jsonBody = "{\"text\": \"" + escapeJson(message) + "\"}";

    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(new URI(incomingWebhookUrl))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

      HttpResponse<Void> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());

      Map<String, Object> result = new HashMap<>();
      result.put("status", httpResponse.statusCode());
      result.put("body", httpResponse.body());

      return new SecurityEventHookResult(result);
    } catch (URISyntaxException e) {

      throw new InvalidConfigurationException("SlackUrl is invalid.", e);

    } catch (IOException | InterruptedException e) {
      throw new HttpNetworkErrorException("Slack request is failed.", e);
    }
  }

  private String escapeJson(String value) {
    return value.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
  }
}
