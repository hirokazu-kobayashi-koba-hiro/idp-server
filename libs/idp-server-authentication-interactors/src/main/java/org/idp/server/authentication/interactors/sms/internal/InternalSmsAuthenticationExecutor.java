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

package org.idp.server.authentication.interactors.sms.internal;

import java.util.Map;
import org.idp.server.authentication.interactors.email.OneTimePassword;
import org.idp.server.authentication.interactors.email.OneTimePasswordGenerator;
import org.idp.server.authentication.interactors.sms.*;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationConfiguration;
import org.idp.server.authentication.interactors.sms.external.*;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.sms.SmsSendResult;
import org.idp.server.platform.notification.sms.SmsSender;
import org.idp.server.platform.notification.sms.SmsSenders;
import org.idp.server.platform.notification.sms.SmsSendingRequest;
import org.idp.server.platform.type.RequestAttributes;

public class InternalSmsAuthenticationExecutor implements SmsAuthenticationExecutor {

  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  SmsSenders smsSenders;
  JsonConverter jsonConverter;

  public InternalSmsAuthenticationExecutor(
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationInteractionQueryRepository interactionQueryRepository,
      SmsSenders smsSenders) {
    this.interactionCommandRepository = interactionCommandRepository;
    this.interactionQueryRepository = interactionQueryRepository;
    this.smsSenders = smsSenders;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SmsAuthenticationType type() {
    return new SmsAuthenticationType("internal");
  }

  @Override
  public SmsAuthenticationExecutionResult challenge(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      SmsAuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      SmsAuthenticationConfiguration configuration) {

    Map<String, Object> detail = configuration.getDetail(configuration.type());
    InternalSmsAuthenticationConfiguration internalSmsAuthenticationConfiguration =
        JsonConverter.snakeCaseInstance()
            .read(detail, InternalSmsAuthenticationConfiguration.class);

    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    SmslVerificationTemplate verificationTemplate =
        internalSmsAuthenticationConfiguration.findTemplate(
            request.optValueAsString("sms_template", "authentication"));
    int retryCountLimitation = internalSmsAuthenticationConfiguration.retryCountLimitation();
    int expireSeconds = internalSmsAuthenticationConfiguration.expireSeconds();
    String phoneNumber = request.getValueAsString("phone_number");
    String body = verificationTemplate.interpolateBody(oneTimePassword.value(), expireSeconds);

    SmsSendingRequest sendingRequest = new SmsSendingRequest(phoneNumber, body);

    SmsSender smsSender = smsSenders.get(internalSmsAuthenticationConfiguration.senderType());
    SmsSendResult sendResult =
        smsSender.send(sendingRequest, internalSmsAuthenticationConfiguration.settings("internal"));

    if (sendResult.isError()) {

      return SmsAuthenticationExecutionResult.clientError(sendResult.data());
    }

    SmsVerificationChallenge verificationChallenge =
        SmsVerificationChallenge.create(oneTimePassword, retryCountLimitation, expireSeconds);

    interactionCommandRepository.register(tenant, identifier, "sms", verificationChallenge);

    return SmsAuthenticationExecutionResult.success(Map.of());
  }

  @Override
  public SmsAuthenticationExecutionResult verify(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      SmsAuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      SmsAuthenticationConfiguration configuration) {

    SmsVerificationChallenge verificationChallenge =
        interactionQueryRepository.get(tenant, identifier, "sms", SmsVerificationChallenge.class);

    String verificationCode = request.optValueAsString("verification_code", "");

    SmsVerificationResult verificationResult = verificationChallenge.verify(verificationCode);

    if (verificationResult.isFailure()) {

      SmsVerificationChallenge countUpVerificationChallenge = verificationChallenge.countUp();
      interactionCommandRepository.update(tenant, identifier, "sms", countUpVerificationChallenge);

      return SmsAuthenticationExecutionResult.clientError(verificationResult.response());
    }

    return SmsAuthenticationExecutionResult.success(Map.of());
  }
}
