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

import org.idp.server.authentication.interactors.sms.SmsAuthenticationConfiguration;
import org.idp.server.authentication.interactors.sms.executor.SmslVerificationTemplate;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.contact.ContactVerificationCodeSender;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.sms.SmsSendResult;
import org.idp.server.platform.notification.sms.SmsSender;
import org.idp.server.platform.notification.sms.SmsSenders;
import org.idp.server.platform.notification.sms.SmsSendingRequest;

/**
 * Sends self-service phone codes using the tenant's existing {@code type: "sms"} authentication
 * configuration (Issue #1416).
 *
 * <p>Mirrors the email sender: sender type, settings, templates, retry cap and expiry are already
 * modelled and operated there, so the self-service flow needs no configuration of its own.
 *
 * <p>SMS has no subject, unlike email. That asymmetry stays inside this class — the {@link
 * ContactVerificationCodeSender} port is only "deliver this code to this value".
 */
public class SmsAuthenticationConfigCodeSender implements ContactVerificationCodeSender {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  SmsSenders smsSenders;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(SmsAuthenticationConfigCodeSender.class);

  public SmsAuthenticationConfigCodeSender(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      SmsSenders smsSenders) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.smsSenders = smsSenders;
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
  public void notifyChanged(
      Tenant tenant,
      ContactVerificationOperation operation,
      String previousValue,
      String newValueMasked) {

    try {
      SmsAuthenticationConfiguration configuration = configuration(tenant);
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
      log.warn(
          "Contact change notice sending failed. operation={}, error={}",
          operation.value(),
          exception.getMessage());
    }
  }

  @Override
  public boolean send(
      Tenant tenant,
      ContactVerificationOperation operation,
      String targetValue,
      String verificationCode) {

    SmsAuthenticationConfiguration configuration = configuration(tenant);
    SmslVerificationTemplate template = configuration.findTemplate(operation.templateKey());
    String body = template.interpolateBody(verificationCode, configuration.expireSeconds());

    SmsSendingRequest sendingRequest = new SmsSendingRequest(targetValue, body);

    SmsSender smsSender = smsSenders.get(configuration.senderType());
    SmsSendResult sendResult = smsSender.send(sendingRequest, configuration.settings());

    if (sendResult.isError()) {
      log.warn("Contact verification code sending failed. operation={}", operation.value());
      return false;
    }
    return true;
  }

  private SmsAuthenticationConfiguration configuration(Tenant tenant) {
    AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "sms");
    AuthenticationInteractionConfig interactionConfig =
        configuration.getAuthenticationConfig("sms-authentication-challenge");
    return jsonConverter.read(
        interactionConfig.execution().details(), SmsAuthenticationConfiguration.class);
  }
}
