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

import org.idp.server.authentication.interactors.sms.SmsAuthenticationConfiguration;
import org.idp.server.authentication.interactors.sms.executor.SmslVerificationTemplate;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.contact.ContactChallengeStart;
import org.idp.server.core.openid.identity.contact.ContactChangeNotifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.core.openid.identity.contact.ContactVerificationGateway;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.sms.SmsSendResult;
import org.idp.server.platform.notification.sms.SmsSender;
import org.idp.server.platform.notification.sms.SmsSenders;
import org.idp.server.platform.notification.sms.SmsSendingRequest;
import org.idp.server.platform.random.OneTimePassword;
import org.idp.server.platform.random.OneTimePasswordGenerator;

/**
 * Drives the SMS code exchange using the tenant's existing {@code type: "sms"} authentication
 * configuration (Issue #1416).
 *
 * <p>Reusing that configuration is the point: sender function, SMTP / HTTP settings, templates,
 * retry cap and expiry are already modelled and operated there, so the self-service flow needs no
 * configuration of its own.
 *
 * <p>That configuration has two shapes, and both are handled here:
 *
 * <ul>
 *   <li><strong>local</strong> — {@code execution.details} describes a sender; idp-server generates
 *       the code, delivers it and later compares it
 *   <li><strong>delegated</strong> — {@code execution.function} is {@code http_request}; an
 *       external service does all three and idp-server keeps only a reference
 * </ul>
 */
public class SmsAuthenticationConfigGateway
    implements ContactVerificationGateway, ContactChangeNotifier {

  /**
   * Timing for a delegated exchange.
   *
   * <p>These live in {@code execution.details}, which a delegated configuration does not have, and
   * the external service enforces its own anyway. What is left for idp-server is its own
   * bookkeeping — when to drop the challenge row, and how often a caller may ask for another code —
   * so these are stated here rather than read as zero from an empty configuration.
   */
  static final int DELEGATED_EXPIRE_SECONDS = 300;

  static final int DELEGATED_RETRY_COUNT_LIMITATION = 5;
  static final int DELEGATED_RESEND_COOLDOWN_SECONDS = 60;

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  SmsSenders smsSenders;
  ExternalContactVerificationExchange externalExchange;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(SmsAuthenticationConfigGateway.class);

  public SmsAuthenticationConfigGateway(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      SmsSenders smsSenders,
      ExternalContactVerificationExchange externalExchange) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.smsSenders = smsSenders;
    this.externalExchange = externalExchange;
  }

  @Override
  public ContactChallengeStart start(
      Tenant tenant, ContactVerificationOperation operation, String targetValue) {

    AuthenticationExecutionConfig execution = challengeExecution(tenant);
    if (ExternalContactVerificationExchange.isDelegated(execution)) {
      return externalExchange.start(execution, operation, targetValue);
    }

    SmsAuthenticationConfiguration configuration = localConfiguration(execution);
    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    SmslVerificationTemplate template = configuration.findTemplate(operation.templateKey());
    String body = template.interpolateBody(oneTimePassword.value(), configuration.expireSeconds());

    SmsSendingRequest sendingRequest = new SmsSendingRequest(targetValue, body);

    SmsSender smsSender = smsSenders.get(configuration.senderType());
    SmsSendResult sendResult = smsSender.send(sendingRequest, configuration.settings());

    if (sendResult.isError()) {
      log.warn("Sms verification code sending failed. operation={}", operation.value());
      return ContactChallengeStart.failure();
    }
    return ContactChallengeStart.internal(oneTimePassword.value());
  }

  @Override
  public boolean verifyCode(
      Tenant tenant,
      ContactVerificationOperation operation,
      ContactVerificationChallenge challenge,
      String submittedCode) {

    if (challenge.isExternal()) {
      return externalExchange.verifyCode(
          verifyExecution(tenant), operation, challenge, submittedCode);
    }
    return challenge.matches(submittedCode);
  }

  @Override
  public int expireSeconds(Tenant tenant, ContactVerificationOperation operation) {
    AuthenticationExecutionConfig execution = challengeExecution(tenant);
    if (ExternalContactVerificationExchange.isDelegated(execution)) {
      return DELEGATED_EXPIRE_SECONDS;
    }
    return localConfiguration(execution).expireSeconds();
  }

  @Override
  public int retryCountLimitation(Tenant tenant, ContactVerificationOperation operation) {
    AuthenticationExecutionConfig execution = challengeExecution(tenant);
    if (ExternalContactVerificationExchange.isDelegated(execution)) {
      return DELEGATED_RETRY_COUNT_LIMITATION;
    }
    return localConfiguration(execution).retryCountLimitation();
  }

  @Override
  public int resendCooldownSeconds(Tenant tenant, ContactVerificationOperation operation) {
    AuthenticationExecutionConfig execution = challengeExecution(tenant);
    if (ExternalContactVerificationExchange.isDelegated(execution)) {
      return DELEGATED_RESEND_COOLDOWN_SECONDS;
    }
    return localConfiguration(execution).resendCooldownSeconds();
  }

  @Override
  public void notifyChanged(
      Tenant tenant,
      ContactVerificationOperation operation,
      String previousValue,
      String newValueMasked) {

    try {
      AuthenticationExecutionConfig execution = challengeExecution(tenant);
      if (ExternalContactVerificationExchange.isDelegated(execution)) {
        // No sender and no templates here: a delegated configuration describes an exchange, not a
        // way to send a plain message. Until tenant-level notification configuration exists, this
        // tenant gets no notice.
        log.info(
            "Contact change notice skipped: no local sender configured. operation={}",
            operation.value());
        return;
      }

      SmsAuthenticationConfiguration configuration = localConfiguration(execution);
      SmslVerificationTemplate template = configuration.findTemplate(operation.noticeTemplateKey());
      String body =
          template.interpolateChangeNotice(SystemDateTime.now().toString(), newValueMasked);

      SmsSendingRequest sendingRequest = new SmsSendingRequest(previousValue, body);

      SmsSender smsSender = smsSenders.get(configuration.senderType());
      SmsSendResult sendResult = smsSender.send(sendingRequest, configuration.settings());

      if (sendResult.isError()) {
        log.warn("Contact change notice sending failed. operation={}", operation.value());
      }
    } catch (Exception exception) {
      // The change is already committed; a courtesy notice must not turn a success into a failure.
      log.warn(
          "Contact change notice sending failed. operation={}, error={}",
          operation.value(),
          exception.getMessage());
    }
  }

  private AuthenticationExecutionConfig challengeExecution(Tenant tenant) {
    return interactionConfig(tenant, "sms-authentication-challenge").execution();
  }

  private AuthenticationExecutionConfig verifyExecution(Tenant tenant) {
    return interactionConfig(tenant, "sms-authentication").execution();
  }

  private AuthenticationInteractionConfig interactionConfig(Tenant tenant, String key) {
    AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "sms");
    return configuration.getAuthenticationConfig(key);
  }

  private SmsAuthenticationConfiguration localConfiguration(
      AuthenticationExecutionConfig execution) {
    return jsonConverter.read(execution.details(), SmsAuthenticationConfiguration.class);
  }
}
