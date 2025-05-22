/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.notification.push.fcm;

import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.oidc.identity.device.NotificationTemplate;

import java.util.Map;

public class FcmConfiguration implements JsonReadable {

  String credential;
  Map<String, NotificationTemplate> templates;

  public FcmConfiguration() {}

  public FcmConfiguration(String credential, Map<String, NotificationTemplate> templates) {
    this.credential = credential;
    this.templates = templates;
  }

  public String credential() {
    return credential;
  }

  public NotificationTemplate findTemplate(String key) {
    return templates.getOrDefault(key, new NotificationTemplate());
  }
}
