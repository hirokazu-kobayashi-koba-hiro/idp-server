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

package org.idp.server.platform.notification.sms;

import org.idp.server.platform.dependency.ComponentFactory;

/**
 * Factory interface for creating SmsSender instances with dependency injection support. This
 * interface enables SmsSender implementations to access dependencies from the DI container, such as
 * OAuthAuthorizationResolvers for OAuth token caching.
 *
 * <p>Usage Guidelines:
 *
 * <ul>
 *   <li>✅ Recommended: Use Factory pattern for new SmsSender implementations
 *   <li>🔄 Migration: Convert existing direct SPI implementations to use this factory
 *   <li>❌ Deprecated: Direct SPI instantiation without dependency injection
 * </ul>
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class MySmsSenderFactory implements SmsSenderFactory {
 *   @Override
 *   public SmsSender create(ApplicationComponentDependencyContainer container) {
 *     OAuthAuthorizationResolvers resolvers = container.resolve(OAuthAuthorizationResolvers.class);
 *     return new MySmsSender(resolvers);
 *   }
 *
 *   @Override
 *   public SmsSenderType type() {
 *     return new SmsSenderType("my_sms_sender");
 *   }
 * }
 * }</pre>
 */
public interface SmsSenderFactory extends ComponentFactory<SmsSender> {

  /**
   * Returns the SMS sender type identifier for this factory. This is used to register and identify
   * the component in plugin loaders.
   *
   * @return the SMS sender type identifier
   */
  SmsSenderType smsSenderType();
}
