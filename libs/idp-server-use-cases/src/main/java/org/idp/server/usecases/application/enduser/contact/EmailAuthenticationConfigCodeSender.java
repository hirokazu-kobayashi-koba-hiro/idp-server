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

package org.idp.server.usecases.application.enduser.contact;

import org.idp.server.authentication.interactors.email.EmailAuthenticationConfiguration;
import org.idp.server.authentication.interactors.email.EmailVerificationTemplate;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.contact.ContactVerificationCodeSender;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.email.EmailSendResult;
import org.idp.server.platform.notification.email.EmailSender;
import org.idp.server.platform.notification.email.EmailSenders;
import org.idp.server.platform.notification.email.EmailSendingRequest;

/**
 * Sends self-service email codes using the tenant's existing {@code type: "email"} authentication
 * configuration (Issue #1416).
 *
 * <p>Reusing that configuration is the point: sender function, SMTP / HTTP settings, templates,
 * retry cap and expiry are already modelled and operated there, so the self-service flow needs no
 * configuration of its own. Only the template key differs per operation, and an undefined key falls
 * back to the configuration's default body.
 *
 * <p>An adapter, not an interactor: it implements the {@link ContactVerificationCodeSender} port
 * and happens to read a configuration type that the login interactors also use.
 */
public class EmailAuthenticationConfigCodeSender implements ContactVerificationCodeSender {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  EmailSenders emailSenders;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(EmailAuthenticationConfigCodeSender.class);

  public EmailAuthenticationConfigCodeSender(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      EmailSenders emailSenders) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.emailSenders = emailSenders;
  }

  @Override
  public int expireSeconds(Tenant tenant, ContactVerificationOperation operation) {
    return configuration(tenant).expireSeconds();
  }

  @Override
  public int retryCountLimitation(Tenant tenant, ContactVerificationOperation operation) {
    return configuration(tenant).retryCountLimitation();
  }

  @Override
  public int resendCooldownSeconds(Tenant tenant, ContactVerificationOperation operation) {
    return configuration(tenant).resendCooldownSeconds();
  }

  @Override
  public boolean send(
      Tenant tenant,
      ContactVerificationOperation operation,
      String targetValue,
      String verificationCode) {

    EmailAuthenticationConfiguration configuration = configuration(tenant);
    EmailVerificationTemplate template = configuration.findTemplate(operation.templateKey());
    String body = template.interpolateBody(verificationCode, configuration.expireSeconds());

    EmailSendingRequest sendingRequest =
        new EmailSendingRequest(configuration.sender(), targetValue, template.subject(), body);

    EmailSender emailSender = emailSenders.get(configuration.function());
    EmailSendResult sendResult = emailSender.send(sendingRequest, configuration.senderConfig());

    if (sendResult.isError()) {
      log.warn("Email verification code sending failed. operation={}", operation.value());
      return false;
    }
    return true;
  }

  private EmailAuthenticationConfiguration configuration(Tenant tenant) {
    AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "email");
    AuthenticationInteractionConfig interactionConfig =
        configuration.getAuthenticationConfig("email-authentication-challenge");
    return jsonConverter.read(
        interactionConfig.execution().details(), EmailAuthenticationConfiguration.class);
  }
}
