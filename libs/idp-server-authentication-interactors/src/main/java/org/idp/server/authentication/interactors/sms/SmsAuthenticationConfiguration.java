/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
