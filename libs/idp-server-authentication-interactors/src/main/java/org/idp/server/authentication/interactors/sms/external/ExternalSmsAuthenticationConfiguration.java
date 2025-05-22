/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.sms.external;

import java.util.Map;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutorType;
import org.idp.server.authentication.interactors.sms.SmsTemplate;
import org.idp.server.authentication.interactors.sms.exception.SmsAuthenticationExecutionConfigNotFoundException;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;

public class ExternalSmsAuthenticationConfiguration implements JsonReadable {
  String type;
  String description;
  OAuthAuthorizationConfiguration oauthAuthorization;
  Map<String, ExternalSmsAuthenticationExecutionConfiguration> executions;
  Map<String, SmsTemplate> templates;

  public ExternalSmsAuthenticationConfiguration() {}

  public FidoUafExecutorType type() {
    return new FidoUafExecutorType(type);
  }

  public String description() {
    return description;
  }

  public OAuthAuthorizationConfiguration oauthAuthorization() {
    if (oauthAuthorization == null) {
      return new OAuthAuthorizationConfiguration();
    }
    return oauthAuthorization;
  }

  public boolean hasAuthorization() {
    return oauthAuthorization != null && oauthAuthorization.exists();
  }

  public Map<String, ExternalSmsAuthenticationExecutionConfiguration> executions() {
    return executions;
  }

  public ExternalSmsAuthenticationExecutionConfiguration getExecutionConfig(String executionType) {
    if (!executions.containsKey(executionType)) {
      throw new SmsAuthenticationExecutionConfigNotFoundException(
          "invalid configuration. type: " + executionType + " is unregistered.");
    }
    return executions.get(executionType);
  }

  public SmsTemplate findtTemplate(String templateKey) {
    return templates.getOrDefault(templateKey, new SmsTemplate());
  }
}
