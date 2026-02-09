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

package org.idp.server.adapters.springboot.application.event;

import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Spring Boot adapter for {@link SecurityEventPublisher}.
 *
 * <p>Provides two publishing strategies:
 *
 * <ul>
 *   <li>{@link #publish(SecurityEvent)} — delegates to Spring's {@link ApplicationEventPublisher},
 *       which delivers the event to an {@code @Async @EventListener} on a separate thread
 *       (fire-and-forget).
 *   <li>{@link #publishSync(SecurityEvent)} — invokes {@code SecurityEventApi.handle()} directly
 *       via {@link IdpServerApplication#rawSecurityEventApi()}, reusing the caller's thread and
 *       transaction. The raw (non-proxied) API is used to avoid a "Transaction already started"
 *       error that would occur if the proxied entry service tried to open a second transaction.
 * </ul>
 *
 * <p>{@code IdpServerApplication} is injected with {@link Lazy @Lazy} to break a circular
 * dependency: {@code IdpServerApplication} depends on this service at construction time, while this
 * service depends on {@code IdpServerApplication} for synchronous dispatch.
 */
@Service
public class SecurityEventPublisherService implements SecurityEventPublisher {

  ApplicationEventPublisher applicationEventPublisher;
  IdpServerApplication idpServerApplication;

  public SecurityEventPublisherService(
      ApplicationEventPublisher applicationEventPublisher,
      @Lazy IdpServerApplication idpServerApplication) {
    this.applicationEventPublisher = applicationEventPublisher;
    this.idpServerApplication = idpServerApplication;
  }

  /** {@inheritDoc} Delegates to Spring's {@code @Async @EventListener} (fire-and-forget). */
  @Override
  public void publish(SecurityEvent securityEvent) {
    applicationEventPublisher.publishEvent(securityEvent);
  }

  /**
   * {@inheritDoc} Invokes the security event handler synchronously on the caller's thread,
   * bypassing the Spring event bus.
   */
  @Override
  public void publishSync(SecurityEvent securityEvent) {
    idpServerApplication
        .rawSecurityEventApi()
        .handle(securityEvent.tenantIdentifier(), securityEvent);
  }
}
