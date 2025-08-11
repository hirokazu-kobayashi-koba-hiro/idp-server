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

package org.idp.server.authentication.interactors.sms.executor;

import java.util.Map;
import org.idp.server.authentication.interactors.AuthenticationExecutionRequest;
import org.idp.server.authentication.interactors.AuthenticationExecutionResult;
import org.idp.server.authentication.interactors.AuthenticationExecutor;
import org.idp.server.authentication.interactors.email.OneTimePassword;
import org.idp.server.authentication.interactors.email.OneTimePasswordGenerator;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationConfiguration;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.sms.SmsSendResult;
import org.idp.server.platform.notification.sms.SmsSender;
import org.idp.server.platform.notification.sms.SmsSenders;
import org.idp.server.platform.notification.sms.SmsSendingRequest;
import org.idp.server.platform.type.RequestAttributes;

public class SmsChallengeAuthenticationExecutor implements AuthenticationExecutor {

  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  SmsSenders smsSenders;
  JsonConverter jsonConverter;

  public SmsChallengeAuthenticationExecutor(
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationInteractionQueryRepository interactionQueryRepository,
      SmsSenders smsSenders) {
    this.interactionCommandRepository = interactionCommandRepository;
    this.interactionQueryRepository = interactionQueryRepository;
    this.smsSenders = smsSenders;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String function() {
    return "sms_authentication_challenge";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    Map<String, Object> detail = configuration.details();
    SmsAuthenticationConfiguration smsAuthenticationConfiguration =
        jsonConverter.read(detail, SmsAuthenticationConfiguration.class);

    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    SmslVerificationTemplate verificationTemplate =
        smsAuthenticationConfiguration.findTemplate(
            request.optValueAsString("template", "authentication"));
    int retryCountLimitation = smsAuthenticationConfiguration.retryCountLimitation();
    int expireSeconds = smsAuthenticationConfiguration.expireSeconds();
    String phoneNumber = request.getValueAsString("phone_number");
    String body = verificationTemplate.interpolateBody(oneTimePassword.value(), expireSeconds);

    SmsSendingRequest sendingRequest = new SmsSendingRequest(phoneNumber, body);

    SmsSender smsSender = smsSenders.get(smsAuthenticationConfiguration.senderType());
    SmsSendResult sendResult =
        smsSender.send(sendingRequest, smsAuthenticationConfiguration.settings());

    if (sendResult.isError()) {

      return AuthenticationExecutionResult.clientError(sendResult.data());
    }

    SmsVerificationChallenge verificationChallenge =
        SmsVerificationChallenge.create(oneTimePassword, retryCountLimitation, expireSeconds);

    interactionCommandRepository.register(
        tenant, identifier, "sms-authentication-challenge", verificationChallenge);

    return AuthenticationExecutionResult.success(Map.of());
  }
}
