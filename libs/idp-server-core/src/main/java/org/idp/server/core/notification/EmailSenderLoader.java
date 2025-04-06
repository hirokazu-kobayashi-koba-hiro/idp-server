package org.idp.server.core.notification;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;

public class EmailSenderLoader {

  private static final Logger log = Logger.getLogger(EmailSenderLoader.class.getName());

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
