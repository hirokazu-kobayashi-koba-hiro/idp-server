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

package org.idp.server.emai.aws.adapter;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.notification.email.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

public class AwsEmailSender implements EmailSender {

  @Override
  public EmailSenderType type() {
    return new EmailSenderType("aws_ses");
  }

  @Override
  public EmailSendResult send(EmailSendingRequest request, EmailSenderSetting setting) {
    String sender = setting.getValueAsString("sender");
    String regionName = setting.optValueAsString("region", "ap-northeast-1");

    Region region = Region.of(regionName);

    try (SesClient sesClient = SesClient.builder().region(region).build()) {
      Destination destination = Destination.builder().toAddresses(request.to()).build();

      Content subject = Content.builder().data(request.subject()).charset("UTF-8").build();

      Content textBody = Content.builder().data(request.body()).charset("UTF-8").build();

      Body body = Body.builder().text(textBody).build();

      Message message = Message.builder().subject(subject).body(body).build();

      SendEmailRequest emailRequest =
          SendEmailRequest.builder()
              .source(sender)
              .destination(destination)
              .message(message)
              .build();

      SendEmailResponse sendEmailResponse = sesClient.sendEmail(emailRequest);
      Map<String, Object> data = new HashMap<>();
      data.put("message_id", sendEmailResponse.messageId());

      return new EmailSendResult(true, data);
    } catch (SesException e) {
      Map<String, Object> data = new HashMap<>();
      data.put("error", "server_error");
      data.put(
          "error_description",
          "Failed to send email via AWS SES: " + e.awsErrorDetails().errorMessage());

      return new EmailSendResult(false, data);
    }
  }
}
