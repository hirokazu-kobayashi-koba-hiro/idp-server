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

package org.idp.server.core.openid.clientinstance.registration;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Publishes the security events of the Client Instance registration flow.
 *
 * <p>The endpoints answer every rejection with the same opaque {@code invalid_request}, because a
 * distinguishable reason would let a caller probe which clients take part in registration, or which
 * check a forged request got past. These events are where the reason is kept instead.
 */
public class ClientInstanceRegistrationEventPublisher {

  SecurityEventPublisher securityEventPublisher;

  public ClientInstanceRegistrationEventPublisher(SecurityEventPublisher securityEventPublisher) {
    this.securityEventPublisher = securityEventPublisher;
  }

  /**
   * Ticket issuance, which is where the server decides what may be registered.
   *
   * <p>Discardable: the outcome is captured by the success or failure event that follows. What it
   * is here for is the shape of the traffic — repeated issuance across client_id values is what
   * enumeration looks like.
   */
  public void publishChallengeIssued(
      Tenant tenant,
      RequestedClientId requestedClientId,
      ClientInstanceIdentifier instanceIdentifier,
      RequestAttributes requestAttributes) {

    Map<String, Object> details = new HashMap<>();
    details.put("client_id", requestedClientId.value());
    details.put("instance_id", instanceIdentifier.value());

    publish(
        tenant,
        DefaultSecurityEventType.client_instance_registration_challenge_issued,
        details,
        requestAttributes);
  }

  public void publishSuccess(
      Tenant tenant,
      RequestedClientId requestedClientId,
      ClientInstanceIdentifier instanceIdentifier,
      String userId,
      RequestAttributes requestAttributes) {

    Map<String, Object> details = new HashMap<>();
    details.put("client_id", requestedClientId.value());
    details.put("instance_id", instanceIdentifier.value());
    if (userId != null) {
      details.put("user_id", userId);
    }

    publish(
        tenant,
        DefaultSecurityEventType.client_instance_registration_success,
        details,
        requestAttributes);
  }

  /**
   * @param operation which of the two endpoints rejected, since both share this event type
   * @param reason the message the caller is deliberately not told
   * @param details what the step knew — the challenge endpoint has client_id, the registration
   *     endpoint carries only the ticket and so may not
   */
  public void publishFailure(
      Tenant tenant,
      String operation,
      String reason,
      Map<String, Object> details,
      RequestAttributes requestAttributes) {

    Map<String, Object> merged = new HashMap<>(details);
    merged.put("operation", operation);
    merged.put("reason", reason);

    publish(
        tenant,
        DefaultSecurityEventType.client_instance_registration_failure,
        merged,
        requestAttributes);
  }

  private void publish(
      Tenant tenant,
      DefaultSecurityEventType type,
      Map<String, Object> details,
      RequestAttributes requestAttributes) {

    SecurityEvent securityEvent =
        new ClientInstanceRegistrationEventCreator(
                tenant, type.toEventType(), details, requestAttributes)
            .create();

    if (type.isSynchronous()) {
      securityEventPublisher.publishSync(securityEvent);
      return;
    }
    securityEventPublisher.publish(securityEvent);
  }
}
