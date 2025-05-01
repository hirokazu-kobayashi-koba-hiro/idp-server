package org.idp.server.adapters.springboot.notification;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.idp.server.core.authentication.email.EmailSenderSetting;
import org.idp.server.core.authentication.notification.EmailSender;
import org.idp.server.core.authentication.notification.EmailSenderType;
import org.idp.server.core.authentication.notification.EmailSendingRequest;

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
