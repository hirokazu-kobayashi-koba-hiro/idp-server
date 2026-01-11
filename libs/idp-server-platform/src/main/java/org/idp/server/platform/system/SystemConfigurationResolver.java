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

package org.idp.server.platform.system;

/**
 * Resolver for system-wide configuration.
 *
 * <p>This interface abstracts the retrieval of system configuration, allowing for different
 * implementations such as cached or direct database access.
 *
 * <p>Similar to {@link org.idp.server.platform.oauth.OAuthAuthorizationResolver}, this resolver
 * pattern enables caching and lazy evaluation of configuration values.
 */
public interface SystemConfigurationResolver {

  /**
   * Resolves the current system configuration.
   *
   * <p>Implementations may cache the result for performance.
   *
   * @return the system configuration
   */
  SystemConfiguration resolve();

  /**
   * Invalidates any cached configuration, forcing a fresh read on the next resolve call.
   *
   * <p>Call this method when configuration has been updated and the cache needs to be refreshed.
   */
  void invalidateCache();
}
