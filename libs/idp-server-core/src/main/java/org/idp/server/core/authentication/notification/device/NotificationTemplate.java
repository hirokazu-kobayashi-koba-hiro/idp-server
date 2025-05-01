package org.idp.server.core.authentication.notification.device;

import org.idp.server.basic.json.JsonReadable;

public class NotificationTemplate implements JsonReadable {

  String subject;
  String body;

  public NotificationTemplate() {}

  public NotificationTemplate(String subject, String body) {
    this.subject = subject;
    this.body = body;
  }

  public String subject() {
    return subject;
  }

  public String body() {
    return body;
  }

  public boolean exists() {
    return subject != null && body != null;
  }
}
