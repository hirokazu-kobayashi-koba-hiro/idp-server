/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.security.event.hooks.slack;

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
