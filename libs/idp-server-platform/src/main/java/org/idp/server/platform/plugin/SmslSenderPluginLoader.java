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

import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.notification.email.EmailSender;
import org.idp.server.platform.notification.email.EmailSenderType;
import org.idp.server.platform.notification.email.EmailSenders;
import org.idp.server.platform.notification.sms.SmsSender;
import org.idp.server.platform.notification.sms.SmsSenderType;
import org.idp.server.platform.notification.sms.SmsSenders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmslSenderPluginLoader extends PluginLoader{

  private static final LoggerWrapper log = LoggerWrapper.getLogger(SmslSenderPluginLoader.class);

  public static SmsSenders load() {

    Map<SmsSenderType, SmsSender> senders = new HashMap<>();
    List<SmsSender> internalSmsSenders = loadFromInternalModule(SmsSender.class);
    for (SmsSender smsSender : internalSmsSenders) {
      senders.put(smsSender.type(), smsSender);
      log.info("Dynamic Registered internal sms sender: " + smsSender.type().name());
    }

    List<SmsSender> externalSmsSenders = loadFromExternalModule(SmsSender.class);
    for (SmsSender smsSender : externalSmsSenders) {
      senders.put(smsSender.type(), smsSender);
      log.info("Dynamic Registered external sms sender: " + smsSender.type().name());
    }

    return new SmsSenders(senders);
  }
}
