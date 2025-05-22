package org.idp.server.authentication.interactors.notification.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.authentication.interactors.notification.EmailSender;
import org.idp.server.authentication.interactors.notification.EmailSenderType;
import org.idp.server.authentication.interactors.notification.EmailSenders;
import org.idp.server.platform.log.LoggerWrapper;

public class EmailSenderPluginLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(EmailSenderPluginLoader.class);

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
