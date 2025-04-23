package org.idp.server.notification.push.fcm;

import org.idp.server.core.authentication.email.EmailSenderSetting;
import org.idp.server.core.authentication.email.EmailVerificationTemplate;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.notification.EmailSenderType;
import org.idp.server.core.notification.device.NotificationTemplate;

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
