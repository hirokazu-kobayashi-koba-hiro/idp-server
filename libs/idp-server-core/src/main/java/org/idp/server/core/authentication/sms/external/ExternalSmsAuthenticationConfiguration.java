package org.idp.server.core.authentication.sms.external;

import java.util.Map;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.core.authentication.fidouaf.FidoUafExecutorType;
import org.idp.server.core.authentication.fidouaf.external.ExternalFidoUafServerExecutionConfiguration;
import org.idp.server.core.authentication.sms.SmsTemplate;
import org.idp.server.core.identity.verification.exception.IdentityVerificationApplicationConfigurationNotFoundException;

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

  public Map<String, ExternalFidoUafServerExecutionConfiguration> executions() {
    return executions;
  }

  public ExternalSmsAuthenticationExecutionConfiguration getExecutionConfig(String executionType) {
    if (!executions.containsKey(executionType)) {
      throw new IdentityVerificationApplicationConfigurationNotFoundException(
          "invalid configuration. type: " + executionType + " is unregistered.");
    }
    return executions.get(executionType);
  }

  public SmsTemplate findtTemplate(String templateKey) {
    return templates.getOrDefault(templateKey, new SmsTemplate());
  }
}
