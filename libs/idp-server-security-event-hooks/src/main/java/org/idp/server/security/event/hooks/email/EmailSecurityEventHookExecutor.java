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

package org.idp.server.security.event.hooks.email;

import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.email.*;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHookType;
import org.idp.server.platform.security.hook.StandardSecurityEventHookType;
import org.idp.server.platform.security.hook.configuration.SecurityEventConfig;
import org.idp.server.platform.security.hook.configuration.SecurityEventExecutionConfig;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;

public class EmailSecurityEventHookExecutor implements SecurityEventHook {

  LoggerWrapper log = LoggerWrapper.getLogger(EmailSecurityEventHookExecutor.class);
  EmailSenders emailSenders;
  JsonConverter jsonConverter;

  public EmailSecurityEventHookExecutor(EmailSenders emailSenders) {
    this.emailSenders = emailSenders;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.Email.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    log.trace("Email hook execution started: event_type={}", securityEvent.type().value());
    log.debug("EmailHookExecutor called");

    SecurityEventConfig securityEventConfig = hookConfiguration.getEvent(securityEvent.type());
    SecurityEventExecutionConfig executionConfig = securityEventConfig.execution();
    EmailSenderConfiguration emailSenderConfiguration =
        jsonConverter.read(executionConfig.details(), EmailSenderConfiguration.class);

    String sender = emailSenderConfiguration.sender();
    String subject = emailSenderConfiguration.subject();
    String body = emailSenderConfiguration.body();
    String email = securityEvent.user().email();

    EmailSendingRequest sendingRequest = new EmailSendingRequest(sender, email, subject, body);

    EmailSender emailSender = emailSenders.get(emailSenderConfiguration.function());

    log.trace(
        "Sending email: recipient={}, sender={}, subject={}, function={}",
        email,
        sender,
        subject,
        emailSenderConfiguration.function());

    EmailSendResult sendResult = emailSender.send(sendingRequest, emailSenderConfiguration);

    if (sendResult.isError()) {
      log.warn(
          "Email notification failed: event_type={}, recipient={}, sender={}, subject={}, function={}, error={}",
          securityEvent.type().value(),
          email,
          sender,
          subject,
          emailSenderConfiguration.function(),
          sendResult.data());
      return SecurityEventHookResult.failure(type(), sendResult.data());
    }

    log.trace("Email notification sent successfully: recipient={}", email);
    return SecurityEventHookResult.success(type(), sendResult.data());
  }
}
