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

import org.idp.server.authentication.interactors.email.EmailAuthenticationConfiguration;
import org.idp.server.authentication.interactors.email.EmailVerificationTemplate;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.contact.ContactChangeNotifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.email.EmailSendResult;
import org.idp.server.platform.notification.email.EmailSender;
import org.idp.server.platform.notification.email.EmailSenders;
import org.idp.server.platform.notification.email.EmailSendingRequest;

/**
 * Tells a replaced email address that it was replaced (Issue #1416).
 *
 * <p>Separate from the code exchange because it is not one: there is no challenge, no code and no
 * verdict, only a message to an address that is no longer on the account. It borrows the tenant's
 * email sender because that is the only place a sender is configured today; a tenant-level
 * notification configuration would be the right home (#1879).
 */
public class EmailContactChangeNotifier implements ContactChangeNotifier {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  EmailSenders emailSenders;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(EmailContactChangeNotifier.class);

  public EmailContactChangeNotifier(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      EmailSenders emailSenders) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.emailSenders = emailSenders;
  }

  @Override
  public void notifyChanged(
      Tenant tenant,
      ContactVerificationOperation operation,
      String previousValue,
      String newValueMasked) {

    try {
      EmailAuthenticationConfiguration configuration = localConfiguration(tenant, operation);

      // A delegated configuration describes an exchange, not a way to send a plain message: no
      // sender, no templates. Until tenant-level notification configuration exists (#1879), such a
      // tenant gets no notice rather than a broken one.
      if (configuration == null || !configuration.exists()) {
        log.info(
            "Contact change notice skipped: no local sender configured. operation={}",
            operation.value());
        return;
      }

      String templateKey = operation.noticeTemplateKey();
      // Without this the generic fallback body applies, and a notice interpolated over a
      // verification-code template reaches the previous address with {VERIFICATION_CODE} still in
      // it — a message that reads like a live code and contains none.
      if (!configuration.hasTemplate(templateKey)) {
        log.info(
            "Contact change notice skipped: template ({}) is not configured. operation={}",
            templateKey,
            operation.value());
        return;
      }

      EmailVerificationTemplate template = configuration.findTemplate(templateKey);
      String body =
          template.interpolateChangeNotice(SystemDateTime.now().toString(), newValueMasked);

      EmailSendingRequest sendingRequest =
          new EmailSendingRequest(configuration.sender(), previousValue, template.subject(), body);

      EmailSender emailSender = emailSenders.get(configuration.function());
      EmailSendResult sendResult = emailSender.send(sendingRequest, configuration.senderConfig());

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

  private EmailAuthenticationConfiguration localConfiguration(
      Tenant tenant, ContactVerificationOperation operation) {

    AuthenticationConfiguration configuration =
        configurationQueryRepository.get(tenant, operation.channel().authenticationConfigType());
    AuthenticationInteractionConfig interactionConfig =
        configuration.getAuthenticationConfig(operation.channel().challengeInteractionKey());
    if (interactionConfig == null) {
      return null;
    }
    return jsonConverter.read(
        interactionConfig.execution().details(), EmailAuthenticationConfiguration.class);
  }
}
