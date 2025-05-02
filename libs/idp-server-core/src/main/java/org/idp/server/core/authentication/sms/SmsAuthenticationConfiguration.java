package org.idp.server.core.authentication.sms;

import java.util.Map;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.authentication.sms.exception.SmsAuthenticationDetailsConfigNotFoundException;

public class SmsAuthenticationConfiguration implements JsonReadable {
  String type;
  Map<String, Map<String, Object>> details;

  public SmsAuthenticationConfiguration() {}

  public SmsAuthenticationType type() {
    return new SmsAuthenticationType(type);
  }

  public Map<String, Map<String, Object>> details() {
    return details;
  }

  public Map<String, Object> getDetail(SmsAuthenticationType type) {
    if (!details.containsKey(type.name())) {
      throw new SmsAuthenticationDetailsConfigNotFoundException(
          "invalid configuration. key: " + type.name() + " is unregistered.");
    }
    return details.get(type.name());
  }
}
