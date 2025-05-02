package org.idp.server.core.authentication.sms.external;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.authentication.sms.*;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class ExternalSmsAuthenticationExecutor implements SmsAuthenticationExecutor {

  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  ExternalSmsAuthenticationHttpClient httpClient;
  JsonConverter jsonConverter;

  public ExternalSmsAuthenticationExecutor(
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationInteractionQueryRepository interactionQueryRepository) {
    this.interactionCommandRepository = interactionCommandRepository;
    this.interactionQueryRepository = interactionQueryRepository;
    this.httpClient = new ExternalSmsAuthenticationHttpClient();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
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

    SmsAuthenticationExecutionResult challengeResult = execute("challenge", request, configuration);

    if (challengeResult.isSuccess()) {
      ExternalSmsAuthenticationTransaction transaction =
          new ExternalSmsAuthenticationTransaction(
              challengeResult.getValueAsStringFromContents(configuration.transactionIdParam()));
      interactionCommandRepository.register(tenant, identifier, "sms", transaction);
    }

    return challengeResult;
  }

  @Override
  public SmsAuthenticationExecutionResult verify(
      Tenant tenant,
      AuthorizationIdentifier identifier,
      SmsAuthenticationExecutionRequest request,
      SmsAuthenticationConfiguration configuration) {

    ExternalSmsAuthenticationTransaction transaction =
        interactionQueryRepository.get(
            tenant, identifier, "sms", ExternalSmsAuthenticationTransaction.class);

    HashMap<String, Object> map = new HashMap<>();
    map.put(configuration.transactionIdParam(), transaction.id());
    map.put(
        configuration.verificationCodeParam(),
        request.getValueAsString(configuration.verificationCodeParam()));

    SmsAuthenticationExecutionRequest externalRequest = new SmsAuthenticationExecutionRequest(map);

    return execute("verify", externalRequest, configuration);
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

    ExternalSmsAuthenticationHttpRequestResult httpRequestResult =
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
