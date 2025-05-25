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

import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
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
