package org.idp.server.core.adapters.security.hook;

import org.idp.server.basic.json.JsonReadable;

public class SlackHookConfig implements JsonReadable {
  String description;
  String incomingWebhookUrl;
  String messageTemplate;

  public SlackHookConfig() {}

  public SlackHookConfig(String description, String incomingWebhookUrl, String messageTemplate) {
    this.description = description;
    this.incomingWebhookUrl = incomingWebhookUrl;
    this.messageTemplate = messageTemplate;
  }

  public String description() {
    return description;
  }

  public String incomingWebhookUrl() {
    return incomingWebhookUrl;
  }

  public String messageTemplate() {
    return messageTemplate;
  }
}
