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

import java.util.Map;
import org.idp.server.core.openid.identity.contact.ContactChangeNotifier;
import org.idp.server.core.openid.identity.contact.ContactChannel;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Sends the change notice over the channel that was changed (Issue #1416).
 *
 * <p>Only the notice needs routing now. The code exchange is selected by {@code execution.function}
 * through {@code ContactVerificationExecutors}, so there is nothing channel-shaped left to dispatch
 * there.
 */
public class ChannelRoutingContactChangeNotifier implements ContactChangeNotifier {

  Map<ContactChannel, ContactChangeNotifier> notifiers;
  LoggerWrapper log = LoggerWrapper.getLogger(ChannelRoutingContactChangeNotifier.class);

  public ChannelRoutingContactChangeNotifier(Map<ContactChannel, ContactChangeNotifier> notifiers) {
    this.notifiers = notifiers;
  }

  @Override
  public void notifyChanged(
      Tenant tenant,
      ContactVerificationOperation operation,
      String previousValue,
      String newValueMasked) {

    ContactChangeNotifier notifier = notifiers.get(operation.channel());
    if (notifier == null) {
      // A missing notifier must not fail a change that already committed.
      log.warn("No contact change notifier for channel ({})", operation.channel().value());
      return;
    }
    notifier.notifyChanged(tenant, operation, previousValue, newValueMasked);
  }
}
