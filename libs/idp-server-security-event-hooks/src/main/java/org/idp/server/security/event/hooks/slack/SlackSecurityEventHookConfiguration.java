/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
