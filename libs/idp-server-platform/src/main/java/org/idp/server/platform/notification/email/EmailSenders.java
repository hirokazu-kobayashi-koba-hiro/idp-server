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

package org.idp.server.platform.notification.email;

import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

public class EmailSenders {

  Map<EmailSenderType, EmailSender> senders;

  public EmailSenders(Map<EmailSenderType, EmailSender> senders) {
    this.senders = senders;
  }

  public EmailSender get(EmailSenderType type) {
    EmailSender emailSender = senders.get(type);

    if (emailSender == null) {
      throw new UnSupportedException("No EmailSender found for type " + type);
    }

    return emailSender;
  }
}
