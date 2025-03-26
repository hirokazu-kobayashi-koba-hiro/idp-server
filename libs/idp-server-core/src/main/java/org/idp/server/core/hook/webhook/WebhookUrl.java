package org.idp.server.core.hook.webhook;

public class WebhookUrl {
  String value;

  public WebhookUrl() {}

  public WebhookUrl(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
