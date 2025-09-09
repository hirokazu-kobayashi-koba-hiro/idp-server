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

package org.idp.server.authentication.interactors.device;

import org.idp.server.platform.dependency.ComponentFactory;
import org.idp.server.platform.notification.NotificationChannel;

/**
 * Factory interface for creating AuthenticationDeviceNotifier instances with dependency injection
 * support. This interface enables AuthenticationDeviceNotifier implementations to access
 * dependencies from the DI container, such as EmailSenders, SmsSenders, or
 * OAuthAuthorizationResolvers for external API calls.
 *
 * <p>Usage Guidelines:
 *
 * <ul>
 *   <li>✅ Recommended: Use Factory pattern for new AuthenticationDeviceNotifier implementations
 *   <li>🔄 Migration: Convert existing direct SPI implementations to use this factory
 *   <li>❌ Deprecated: Direct SPI instantiation without dependency injection
 * </ul>
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class EmailNotifierFactory implements AuthenticationDeviceNotifierFactory {
 *   @Override
 *   public AuthenticationDeviceNotifier create(ApplicationComponentDependencyContainer container) {
 *     EmailSenders emailSenders = container.resolve(EmailSenders.class);
 *     return new EmailAuthenticationDeviceNotifier(emailSenders);
 *   }
 *
 *   @Override
 *   public NotificationChannel notificationChannel() {
 *     return new NotificationChannel("email");
 *   }
 * }
 * }</pre>
 */
public interface AuthenticationDeviceNotifierFactory
    extends ComponentFactory<AuthenticationDeviceNotifier> {

  /**
   * Returns the notification channel identifier for this factory. This is used to register and
   * identify the component in plugin loaders.
   *
   * @return the notification channel identifier
   */
  NotificationChannel notificationChannel();
}
