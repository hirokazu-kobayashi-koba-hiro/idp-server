/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.authentication.interactors.sms.external;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.sms.*;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

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
      AuthenticationTransactionIdentifier identifier,
      SmsAuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      SmsAuthenticationConfiguration configuration) {

    SmsAuthenticationExecutionResult challengeResult =
        execute("challenge", request, requestAttributes, configuration);

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
      AuthenticationTransactionIdentifier identifier,
      SmsAuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
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

    return execute("verify", externalRequest, requestAttributes, configuration);
  }

  private SmsAuthenticationExecutionResult execute(
      String executionType,
      SmsAuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      SmsAuthenticationConfiguration configuration) {

    Map<String, Object> detail = configuration.getDetail(type());
    ExternalSmsAuthenticationConfiguration smsConfiguration =
        jsonConverter.read(detail, ExternalSmsAuthenticationConfiguration.class);

    ExternalSmsAuthenticationExecutionConfiguration executionConfiguration =
        smsConfiguration.getExecutionConfig(executionType);

    ExternalSmsAuthenticationHttpRequestResult httpRequestResult =
        httpClient.execute(request, requestAttributes, executionConfiguration);

    if (httpRequestResult.isClientError()) {
      return SmsAuthenticationExecutionResult.clientError(httpRequestResult.responseBody());
    }

    if (httpRequestResult.isServerError()) {
      return SmsAuthenticationExecutionResult.serverError(httpRequestResult.responseBody());
    }

    return SmsAuthenticationExecutionResult.success(httpRequestResult.responseBody());
  }
}
