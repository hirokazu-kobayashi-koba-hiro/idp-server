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

package org.idp.server.platform.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ComponentFactory;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Base class for plugin loaders that support dependency injection via ComponentFactory pattern.
 * Provides unified loading mechanism for both factory-based (new) and direct SPI (legacy)
 * approaches.
 *
 * @param <T> the component type
 * @param <F> the factory type that creates components of type T
 */
public abstract class DependencyAwarePluginLoader<T, F extends ComponentFactory<T>>
    extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(DependencyAwarePluginLoader.class);

  /**
   * Loads components using the factory pattern with dependency injection support. This is the
   * recommended approach for new implementations.
   *
   * @param factoryClass the factory class to load
   * @param container the dependency injection container
   * @return map of component type to component instance
   */
  protected static <T, F extends ComponentFactory<T>> Map<String, T> loadWithDependencies(
      Class<F> factoryClass, ApplicationComponentDependencyContainer container) {

    Map<String, T> components = new HashMap<>();

    // Load from internal modules
    List<F> internalFactories = loadFromInternalModule(factoryClass);
    for (F factory : internalFactories) {
      T component = factory.create(container);
      components.put(factory.type(), component);
      log.info(
          "Loaded component via factory (internal): {} -> {}",
          factory.type(),
          component.getClass().getName());
    }

    // Load from external modules
    List<F> externalFactories = loadFromExternalModule(factoryClass);
    for (F factory : externalFactories) {
      T component = factory.create(container);
      components.put(factory.type(), component);
      log.info(
          "Loaded component via factory (external): {} -> {}",
          factory.type(),
          component.getClass().getName());
    }

    return components;
  }

  /**
   * Loads components using legacy direct SPI approach (without DI). This method is provided for
   * backward compatibility but should be avoided for new implementations.
   *
   * @param componentClass the component class to load directly
   * @return map of component function to component instance
   */
  protected static <T> Map<String, T> loadLegacyComponents(
      Class<T> componentClass, java.util.function.Function<T, String> typeExtractor) {

    Map<String, T> components = new HashMap<>();

    // Load from internal modules
    List<T> internalComponents = loadFromInternalModule(componentClass);
    for (T component : internalComponents) {
      String type = typeExtractor.apply(component);
      components.put(type, component);
      log.warn(
          "Loaded component via legacy SPI (internal): {} -> {} - Consider migrating to Factory pattern",
          type,
          component.getClass().getName());
    }

    // Load from external modules
    List<T> externalComponents = loadFromExternalModule(componentClass);
    for (T component : externalComponents) {
      String type = typeExtractor.apply(component);
      components.put(type, component);
      log.warn(
          "Loaded component via legacy SPI (external): {} -> {} - Consider migrating to Factory pattern",
          type,
          component.getClass().getName());
    }

    return components;
  }
}
