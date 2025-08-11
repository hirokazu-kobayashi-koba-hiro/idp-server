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

package org.idp.server.authentication.interactors.email.executor;

import java.util.Map;
import org.idp.server.authentication.interactors.AuthenticationExecutionRequest;
import org.idp.server.authentication.interactors.AuthenticationExecutionResult;
import org.idp.server.authentication.interactors.AuthenticationExecutor;
import org.idp.server.authentication.interactors.email.EmailAuthenticationConfiguration;
import org.idp.server.authentication.interactors.email.EmailVerificationTemplate;
import org.idp.server.authentication.interactors.email.OneTimePassword;
import org.idp.server.authentication.interactors.email.OneTimePasswordGenerator;
import org.idp.server.authentication.interactors.sms.executor.SmsVerificationChallenge;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.email.EmailSendResult;
import org.idp.server.platform.notification.email.EmailSender;
import org.idp.server.platform.notification.email.EmailSenders;
import org.idp.server.platform.notification.email.EmailSendingRequest;
import org.idp.server.platform.type.RequestAttributes;

public class EmailChallengeAuthenticationExecutor implements AuthenticationExecutor {

  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  EmailSenders emailSenders;
  JsonConverter jsonConverter;

  public EmailChallengeAuthenticationExecutor(
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationInteractionQueryRepository interactionQueryRepository,
      EmailSenders emailSenders) {
    this.interactionCommandRepository = interactionCommandRepository;
    this.interactionQueryRepository = interactionQueryRepository;
    this.emailSenders = emailSenders;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String function() {
    return "email_authentication_challenge";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    Map<String, Object> detail = configuration.details();
    EmailAuthenticationConfiguration emailAuthenticationConfiguration =
        jsonConverter.read(detail, EmailAuthenticationConfiguration.class);

    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    String email = request.optValueAsString("email", "");
    String sender = emailAuthenticationConfiguration.sender();
    EmailVerificationTemplate verificationTemplate =
        emailAuthenticationConfiguration.findTemplate(
            request.optValueAsString("template", "authentication"));
    String subject = verificationTemplate.subject();
    int retryCountLimitation = emailAuthenticationConfiguration.retryCountLimitation();
    int expireSeconds = emailAuthenticationConfiguration.expireSeconds();

    String body = verificationTemplate.interpolateBody(oneTimePassword.value(), expireSeconds);

    EmailSendingRequest sendingRequest = new EmailSendingRequest(sender, email, subject, body);

    EmailSender emailSender = emailSenders.get(emailAuthenticationConfiguration.senderType());
    EmailSendResult sendResult =
        emailSender.send(sendingRequest, emailAuthenticationConfiguration.settings());

    if (sendResult.isError()) {

      return AuthenticationExecutionResult.clientError(sendResult.data());
    }

    SmsVerificationChallenge verificationChallenge =
        SmsVerificationChallenge.create(oneTimePassword, retryCountLimitation, expireSeconds);

    interactionCommandRepository.register(
        tenant, identifier, "email-authentication-challenge", verificationChallenge);

    return AuthenticationExecutionResult.success(Map.of());
  }
}
