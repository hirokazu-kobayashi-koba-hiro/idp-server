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
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.contact.ContactChangeNotifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.sms.SmsSendResult;
import org.idp.server.platform.notification.sms.SmsSender;
import org.idp.server.platform.notification.sms.SmsSenders;
import org.idp.server.platform.notification.sms.SmsSendingRequest;

/** Tells a replaced phone number that it was replaced. See {@link EmailContactChangeNotifier}. */
public class SmsContactChangeNotifier implements ContactChangeNotifier {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  SmsSenders smsSenders;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(SmsContactChangeNotifier.class);

  public SmsContactChangeNotifier(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      SmsSenders smsSenders) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.smsSenders = smsSenders;
  }

  @Override
  public void notifyChanged(
      Tenant tenant,
      ContactVerificationOperation operation,
      String previousValue,
      String newValueMasked) {

    try {
      SmsAuthenticationConfiguration configuration = localConfiguration(tenant, operation);

      if (configuration == null || !configuration.exists()) {
        log.info(
            "Contact change notice skipped: no local sender configured. operation={}",
            operation.value());
        return;
      }

      String templateKey = operation.noticeTemplateKey();
      if (!configuration.hasTemplate(templateKey)) {
        log.info(
            "Contact change notice skipped: template ({}) is not configured. operation={}",
            templateKey,
            operation.value());
        return;
      }

      SmslVerificationTemplate template = configuration.findTemplate(templateKey);
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

  private SmsAuthenticationConfiguration localConfiguration(
      Tenant tenant, ContactVerificationOperation operation) {

    AuthenticationConfiguration configuration =
        configurationQueryRepository.get(tenant, operation.channel().authenticationConfigType());
    AuthenticationInteractionConfig interactionConfig =
        configuration.getAuthenticationConfig(operation.channel().challengeInteractionKey());
    if (interactionConfig == null) {
      return null;
    }
    return jsonConverter.read(
        interactionConfig.execution().details(), SmsAuthenticationConfiguration.class);
  }
}
