package org.idp.server.core.authentication.sms.external;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.authentication.fidouaf.external.ExternalFidoUafServerHttpRequestResult;
import org.idp.server.core.authentication.sms.*;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class ExternalSmsAuthenticationExecutor implements SmsAuthenticationExecutor {

  ExternalSmsAuthenticationHttpClient httpClient;
  JsonConverter jsonConverter;

  public ExternalSmsAuthenticationExecutor() {
    this.httpClient = new ExternalSmsAuthenticationHttpClient();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public SmsAuthenticationType type() {
    return new SmsAuthenticationType("external");
  }

  @Override
  public SmsAuthenticationExecutionResult challenge(
      Tenant tenant,
      AuthorizationIdentifier identifier,
      SmsAuthenticationExecutionRequest request,
      SmsAuthenticationConfiguration configuration) {
    return execute("challenge", request, configuration);
  }

  @Override
  public SmsAuthenticationExecutionResult verify(
      Tenant tenant,
      AuthorizationIdentifier identifier,
      SmsAuthenticationExecutionRequest request,
      SmsAuthenticationConfiguration configuration) {
    return execute("verify", request, configuration);
  }

  private SmsAuthenticationExecutionResult execute(
      String executionType,
      SmsAuthenticationExecutionRequest request,
      SmsAuthenticationConfiguration configuration) {

    Map<String, Object> detail = configuration.getDetail(type());
    ExternalSmsAuthenticationConfiguration externalFidoUafServerConfiguration =
        jsonConverter.read(detail, ExternalSmsAuthenticationConfiguration.class);

    ExternalSmsAuthenticationExecutionConfiguration executionConfiguration =
        externalFidoUafServerConfiguration.getExecutionConfig(executionType);
    OAuthAuthorizationConfiguration oAuthAuthorizationConfiguration =
        externalFidoUafServerConfiguration.oauthAuthorization();

    ExternalFidoUafServerHttpRequestResult httpRequestResult =
        httpClient.execute(request, executionConfiguration, oAuthAuthorizationConfiguration);

    if (httpRequestResult.isClientError()) {
      return SmsAuthenticationExecutionResult.clientError(httpRequestResult.responseBody());
    }

    if (httpRequestResult.isServerError()) {
      return SmsAuthenticationExecutionResult.serverError(httpRequestResult.responseBody());
    }

    return SmsAuthenticationExecutionResult.success(httpRequestResult.responseBody());
  }
}
