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

package org.idp.server.platform.security;

/**
 * Publishes {@link SecurityEvent}s for audit logging, statistics, and hook execution.
 *
 * <p>Implementations provide two processing modes:
 *
 * <ul>
 *   <li>{@link #publish} — asynchronous (fire-and-forget). The caller does not wait for event
 *       processing to complete.
 *   <li>{@link #publishSync} — synchronous. Event processing (log persistence, statistics update,
 *       hook invocation) completes within the caller's thread and transaction before the HTTP
 *       response is returned.
 * </ul>
 *
 * @see SecurityEvent
 * @see org.idp.server.platform.security.event.DefaultSecurityEventType#isSynchronous()
 */
public interface SecurityEventPublisher {

  /** Publish the event asynchronously. The caller returns immediately. */
  void publish(SecurityEvent securityEvent);

  /**
   * Publish the event synchronously. The caller blocks until all event handlers (audit log,
   * statistics, hooks) have finished processing.
   */
  void publishSync(SecurityEvent securityEvent);
}
