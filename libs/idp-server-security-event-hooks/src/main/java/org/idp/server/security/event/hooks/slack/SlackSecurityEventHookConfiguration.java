package org.idp.server.security.event.hooks.slack;

import java.util.Map;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.platform.security.event.SecurityEventType;

public class SlackSecurityEventHookConfiguration implements JsonReadable {

  SlackHookConfig base;
  Map<String, SlackHookConfig> overlays;

  public SlackSecurityEventHookConfiguration() {}

  public SlackSecurityEventHookConfiguration(
      SlackHookConfig base, Map<String, SlackHookConfig> overlays) {
    this.base = base;
    this.overlays = overlays;
  }

  public String incomingWebhookUrl(SecurityEventType type) {
    if (overlays.containsKey(type.value())) {
      return overlays.get(type.value()).incomingWebhookUrl();
    }
    return base.incomingWebhookUrl();
  }

  public String messageTemplate(SecurityEventType type) {
    if (overlays.containsKey(type.value())) {
      return overlays.get(type.value()).messageTemplate();
    }
    return base.messageTemplate();
  }
}
