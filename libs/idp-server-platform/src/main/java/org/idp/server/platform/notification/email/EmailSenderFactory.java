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

import org.idp.server.platform.dependency.ComponentFactory;

/**
 * Factory interface for creating EmailSender instances with dependency injection support. This
 * interface enables EmailSender implementations to access dependencies from the DI container, such
 * as OAuthAuthorizationResolvers for OAuth token caching.
 *
 * <p>Usage Guidelines:
 *
 * <ul>
 *   <li>✅ Recommended: Use Factory pattern for new EmailSender implementations
 *   <li>🔄 Migration: Convert existing direct SPI implementations to use this factory
 *   <li>❌ Deprecated: Direct SPI instantiation without dependency injection
 * </ul>
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class MyEmailSenderFactory implements EmailSenderFactory {
 *   @Override
 *   public EmailSender create(ApplicationComponentDependencyContainer container) {
 *     OAuthAuthorizationResolvers resolvers = container.resolve(OAuthAuthorizationResolvers.class);
 *     return new MyEmailSender(resolvers);
 *   }
 *
 *   @Override
 *   public String type() {
 *     return "my_email_sender";
 *   }
 * }
 * }</pre>
 */
public interface EmailSenderFactory extends ComponentFactory<EmailSender> {
  // Inherits create(ApplicationComponentDependencyContainer) and type() from ComponentFactory
}
