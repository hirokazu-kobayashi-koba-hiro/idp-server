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

package org.idp.server.platform.dependency;

/**
 * Base interface for component factories that support dependency injection. This interface allows
 * SPI-based components to access dependencies from the DI container.
 *
 * @param <T> the type of component this factory creates
 */
public interface ComponentFactory<T> {

  /**
   * Creates a component instance with dependencies injected from the container.
   *
   * @param container the dependency injection container
   * @return the created component instance
   */
  T create(ApplicationComponentDependencyContainer container);

  /**
   * Returns the type identifier for this factory. This is used to register and identify the
   * component in plugin loaders.
   *
   * @return the type identifier
   */
  String type();
}
