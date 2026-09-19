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

package org.idp.server.contact.adapter;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.email.EmailAuthenticationConfiguration;
import org.idp.server.authentication.interactors.email.EmailVerificationTemplate;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.core.openid.identity.contact.execution.ContactExecutionRequest;
import org.idp.server.core.openid.identity.contact.execution.ContactExecutionResult;
import org.idp.server.core.openid.identity.contact.execution.ContactVerificationExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.email.EmailSendResult;
import org.idp.server.platform.notification.email.EmailSender;
import org.idp.server.platform.notification.email.EmailSenders;
import org.idp.server.platform.notification.email.EmailSendingRequest;
import org.idp.server.platform.random.OneTimePassword;
import org.idp.server.platform.random.OneTimePasswordGenerator;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Generates and delivers an email code from the tenant's {@code email_authentication_challenge}
 * configuration (Issue #1416).
 *
 * <p>The contact counterpart of {@code EmailChallengeAuthenticationExecutor}, reading the same
 * {@code execution.details} — sender function, SMTP / HTTP settings, templates, expiry — so a
 * tenant needs no configuration of its own for self-service. The only difference is where the code
 * goes afterwards: the login executor writes it to {@code authentication_interaction} keyed by the
 * transaction, and this one hands it back to be put on the challenge row.
 */
public class EmailChallengeContactVerificationExecutor implements ContactVerificationExecutor {

  EmailSenders emailSenders;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public EmailChallengeContactVerificationExecutor(EmailSenders emailSenders) {
    this.emailSenders = emailSenders;
  }

  @Override
  public String function() {
    return "email_authentication_challenge";
  }

  @Override
  public ContactExecutionResult execute(
      Tenant tenant,
      ContactVerificationChallenge challenge,
      ContactExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    EmailAuthenticationConfiguration emailConfiguration =
        jsonConverter.read(configuration.details(), EmailAuthenticationConfiguration.class);

    String email = request.optValueAsString("email", "");
    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    EmailVerificationTemplate template =
        emailConfiguration.findTemplate(request.optValueAsString("template", "authentication"));
    String body =
        template.interpolateBody(oneTimePassword.value(), emailConfiguration.expireSeconds());

    EmailSendingRequest sendingRequest =
        new EmailSendingRequest(emailConfiguration.sender(), email, template.subject(), body);

    EmailSender emailSender = emailSenders.get(emailConfiguration.function());
    EmailSendResult sendResult =
        emailSender.send(sendingRequest, emailConfiguration.senderConfig());

    if (sendResult.isError()) {
      Map<String, Object> contents = new HashMap<>(sendResult.data());
      contents.putIfAbsent("error", "server_error");
      contents.putIfAbsent("error_description", "failed to send the verification code.");
      return ContactExecutionResult.error(500, contents);
    }

    return ContactExecutionResult.successWithCode(new HashMap<>(), oneTimePassword.value());
  }
}
