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


package org.idp.server.adapters.springboot.application.delegation;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.idp.server.platform.notification.EmailSender;
import org.idp.server.platform.notification.EmailSenderSetting;
import org.idp.server.platform.notification.EmailSenderType;
import org.idp.server.platform.notification.EmailSendingRequest;

public class SmtpEmailSender implements EmailSender {

  @Override
  public EmailSenderType type() {
    return EmailSenderType.SMTP;
  }

  @Override
  public void send(EmailSendingRequest request, EmailSenderSetting setting) {
    Properties props = new Properties();
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.host", setting.getValueAsString("host"));
    props.put("mail.smtp.port", setting.getValueAsInt("port"));

    Session session =
        Session.getInstance(
            props,
            new Authenticator() {
              @Override
              protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                    setting.getValueAsString("username"), setting.getValueAsString("password"));
              }
            });

    try {
      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(request.from()));
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(request.to()));
      message.setSubject(request.subject());
      message.setText(request.body());

      Transport.send(message);

    } catch (MessagingException e) {
      throw new RuntimeException("Failed to send email", e);
    }
  }
}
