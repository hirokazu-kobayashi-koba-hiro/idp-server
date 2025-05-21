package org.idp.server.core.authentication.notification;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.platform.log.LoggerWrapper;

public class EmailSenderLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(EmailSenderLoader.class);

  public static EmailSenders load() {

    Map<EmailSenderType, EmailSender> senders = new HashMap<>();
    ServiceLoader<EmailSender> serviceLoader = ServiceLoader.load(EmailSender.class);
    for (EmailSender emailSender : serviceLoader) {
      senders.put(emailSender.type(), emailSender);
      log.info("Dynamic Registered email sender: " + emailSender.type().name());
    }

    return new EmailSenders(senders);
  }
}
