/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
