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


package org.idp.server.platform.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.platform.notification.EmailSender;
import org.idp.server.platform.notification.EmailSenderType;
import org.idp.server.platform.notification.EmailSenders;
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
