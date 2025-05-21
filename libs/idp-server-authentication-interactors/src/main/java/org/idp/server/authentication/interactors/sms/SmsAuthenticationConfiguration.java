package org.idp.server.authentication.interactors.sms;

import java.util.Map;
import org.idp.server.authentication.interactors.sms.exception.SmsAuthenticationDetailsConfigNotFoundException;
import org.idp.server.basic.json.JsonReadable;

public class SmsAuthenticationConfiguration implements JsonReadable {
  String type;
  String transactionIdParam;
  String verificationCodeParam;
  Map<String, Map<String, Object>> details;

  public SmsAuthenticationConfiguration() {}

  public SmsAuthenticationType type() {
    return new SmsAuthenticationType(type);
  }

  public String transactionIdParam() {
    return transactionIdParam;
  }

  public String verificationCodeParam() {
    return verificationCodeParam;
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
