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

package org.idp.server.usecases.application.enduser.contact;

import java.util.Map;
import org.idp.server.core.openid.identity.contact.ContactChannel;
import org.idp.server.core.openid.identity.contact.ContactVerificationCodeSender;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Routes a self-service code to the sender for its channel (Issue #1416).
 *
 * <p>Lets {@code ContactVerificationService} depend on one port while email and phone read entirely
 * different tenant configurations underneath.
 */
public class ChannelRoutingCodeSender implements ContactVerificationCodeSender {

  Map<ContactChannel, ContactVerificationCodeSender> senders;

  public ChannelRoutingCodeSender(Map<ContactChannel, ContactVerificationCodeSender> senders) {
    this.senders = senders;
  }

  @Override
  public int expireSeconds(Tenant tenant, ContactVerificationOperation operation) {
    return sender(operation).expireSeconds(tenant, operation);
  }

  @Override
  public int retryCountLimitation(Tenant tenant, ContactVerificationOperation operation) {
    return sender(operation).retryCountLimitation(tenant, operation);
  }

  @Override
  public int resendCooldownSeconds(Tenant tenant, ContactVerificationOperation operation) {
    return sender(operation).resendCooldownSeconds(tenant, operation);
  }

  @Override
  public void notifyChanged(
      Tenant tenant,
      ContactVerificationOperation operation,
      String previousValue,
      String newValueMasked) {
    sender(operation).notifyChanged(tenant, operation, previousValue, newValueMasked);
  }

  @Override
  public boolean send(
      Tenant tenant,
      ContactVerificationOperation operation,
      String targetValue,
      String verificationCode) {
    return sender(operation).send(tenant, operation, targetValue, verificationCode);
  }

  private ContactVerificationCodeSender sender(ContactVerificationOperation operation) {
    ContactVerificationCodeSender sender = senders.get(operation.channel());
    if (sender == null) {
      throw new UnSupportedException("no code sender for channel " + operation.channel().value());
    }
    return sender;
  }
}
