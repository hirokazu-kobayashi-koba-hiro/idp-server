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
import org.idp.server.core.openid.identity.contact.ContactChallengeStart;
import org.idp.server.core.openid.identity.contact.ContactChangeNotifier;
import org.idp.server.core.openid.identity.contact.ContactChannel;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.core.openid.identity.contact.ContactVerificationGateway;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Routes a self-service exchange to the gateway for its channel (Issue #1416).
 *
 * <p>Lets {@code ContactVerificationService} depend on one port while email and phone read entirely
 * different tenant configurations underneath.
 */
public class ChannelRoutingGateway implements ContactVerificationGateway, ContactChangeNotifier {

  Map<ContactChannel, ContactVerificationGateway> gateways;
  Map<ContactChannel, ContactChangeNotifier> notifiers;

  public ChannelRoutingGateway(
      Map<ContactChannel, ContactVerificationGateway> gateways,
      Map<ContactChannel, ContactChangeNotifier> notifiers) {
    this.gateways = gateways;
    this.notifiers = notifiers;
  }

  @Override
  public ContactChallengeStart start(
      Tenant tenant, ContactVerificationOperation operation, String targetValue) {
    return gateway(operation).start(tenant, operation, targetValue);
  }

  @Override
  public boolean verifyCode(
      Tenant tenant,
      ContactVerificationOperation operation,
      ContactVerificationChallenge challenge,
      String submittedCode) {
    return gateway(operation).verifyCode(tenant, operation, challenge, submittedCode);
  }

  @Override
  public int expireSeconds(Tenant tenant, ContactVerificationOperation operation) {
    return gateway(operation).expireSeconds(tenant, operation);
  }

  @Override
  public int retryCountLimitation(Tenant tenant, ContactVerificationOperation operation) {
    return gateway(operation).retryCountLimitation(tenant, operation);
  }

  @Override
  public int resendCooldownSeconds(Tenant tenant, ContactVerificationOperation operation) {
    return gateway(operation).resendCooldownSeconds(tenant, operation);
  }

  @Override
  public void notifyChanged(
      Tenant tenant,
      ContactVerificationOperation operation,
      String previousValue,
      String newValueMasked) {
    notifier(operation).notifyChanged(tenant, operation, previousValue, newValueMasked);
  }

  private ContactVerificationGateway gateway(ContactVerificationOperation operation) {
    ContactVerificationGateway gateway = gateways.get(operation.channel());
    if (gateway == null) {
      throw new UnSupportedException(
          String.format("unsupported contact channel (%s)", operation.channel().value()));
    }
    return gateway;
  }

  private ContactChangeNotifier notifier(ContactVerificationOperation operation) {
    ContactChangeNotifier notifier = notifiers.get(operation.channel());
    if (notifier == null) {
      throw new UnSupportedException(
          String.format("unsupported contact channel (%s)", operation.channel().value()));
    }
    return notifier;
  }
}
