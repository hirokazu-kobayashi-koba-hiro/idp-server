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
import org.idp.server.core.hook.*;
import org.idp.server.core.hook.notification.NotificationTemplateInterpolator;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.exception.InvalidConfigurationException;

public class SlacklNotificationHookExecutor implements HookExecutor {

  HttpClient httpClient;

  public SlacklNotificationHookExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
  }

  @Override
  public HookResult execute(
      Tenant tenant, HookTriggerType type, HookRequest request, HookConfiguration configuration) {
    String url = configuration.slackUrl();
    if (url == null) {
      return new HookResult(Map.of("status", 500, "error", "invalid_configuration"));
    }

    String template = configuration.slackMessageTemplate();

    Map<String, Object> context = new java.util.HashMap<>(request.toMap());
    context.put("trigger", type.name());

    NotificationTemplateInterpolator notificationTemplateInterpolator =
        new NotificationTemplateInterpolator(template, context);
    String message = notificationTemplateInterpolator.interpolate();

    String jsonBody = "{\"text\": \"" + escapeJson(message) + "\"}";

    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(new URI(url))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

      HttpResponse<Void> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());

      Map<String, Object> result = new HashMap<>();
      result.put("status", httpResponse.statusCode());
      result.put("body", httpResponse.body());

      return new HookResult(result);
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
