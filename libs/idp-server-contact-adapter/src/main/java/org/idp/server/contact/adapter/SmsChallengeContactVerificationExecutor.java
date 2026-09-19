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
import org.idp.server.authentication.interactors.sms.SmsAuthenticationConfiguration;
import org.idp.server.authentication.interactors.sms.executor.SmslVerificationTemplate;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.core.openid.identity.contact.execution.ContactExecutionRequest;
import org.idp.server.core.openid.identity.contact.execution.ContactExecutionResult;
import org.idp.server.core.openid.identity.contact.execution.ContactVerificationExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.sms.SmsSendResult;
import org.idp.server.platform.notification.sms.SmsSender;
import org.idp.server.platform.notification.sms.SmsSenders;
import org.idp.server.platform.notification.sms.SmsSendingRequest;
import org.idp.server.platform.random.OneTimePassword;
import org.idp.server.platform.random.OneTimePasswordGenerator;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Generates and delivers an SMS code from the tenant's {@code sms_authentication_challenge}
 * configuration (Issue #1416).
 *
 * <p>The contact counterpart of {@code SmsChallengeAuthenticationExecutor}. It reads {@code
 * phone_number} from the request material under the same name that executor does, which is the name
 * the channel's {@code request.schema} declares and the name every SMS provider integration in this
 * repository already expects.
 */
public class SmsChallengeContactVerificationExecutor implements ContactVerificationExecutor {

  SmsSenders smsSenders;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public SmsChallengeContactVerificationExecutor(SmsSenders smsSenders) {
    this.smsSenders = smsSenders;
  }

  @Override
  public String function() {
    return "sms_authentication_challenge";
  }

  @Override
  public ContactExecutionResult execute(
      Tenant tenant,
      ContactVerificationChallenge challenge,
      ContactExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    SmsAuthenticationConfiguration smsConfiguration =
        jsonConverter.read(configuration.details(), SmsAuthenticationConfiguration.class);

    String phoneNumber = request.optValueAsString("phone_number", "");
    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    SmslVerificationTemplate template =
        smsConfiguration.findTemplate(request.optValueAsString("template", "authentication"));
    String body =
        template.interpolateBody(oneTimePassword.value(), smsConfiguration.expireSeconds());

    SmsSendingRequest sendingRequest = new SmsSendingRequest(phoneNumber, body);

    SmsSender smsSender = smsSenders.get(smsConfiguration.senderType());
    SmsSendResult sendResult = smsSender.send(sendingRequest, smsConfiguration.settings());

    if (sendResult.isError()) {
      Map<String, Object> contents = new HashMap<>(sendResult.data());
      contents.putIfAbsent("error", "server_error");
      contents.putIfAbsent("error_description", "failed to send the verification code.");
      return ContactExecutionResult.error(500, contents);
    }

    return ContactExecutionResult.successWithCode(new HashMap<>(), oneTimePassword.value());
  }
}
