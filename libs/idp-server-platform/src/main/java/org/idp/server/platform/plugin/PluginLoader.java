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

import org.idp.server.platform.log.LoggerWrapper;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class PluginLoader {

    private static final String directory = "plugins";
    private static final LoggerWrapper log = LoggerWrapper.getLogger(PluginLoader.class);

    public static <T> List<T> loadFromInternalModule(Class<T> type) {
        ServiceLoader<T> serviceLoader = ServiceLoader.load(type);

        List<T> extensions = new ArrayList<>();
        for (T impl : serviceLoader) {
            log.info("PluginLoader Loaded internal module: " + impl.getClass().getName());
            extensions.add(impl);
        }
        return extensions;
    }

    public static <T> List<T> loadFromExternalModule(Class<T> type) {

        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            return Collections.emptyList();
        }

        try {
            URL[] urls = Arrays.stream(jars)
                    .map(f -> {
                        try {
                            return f.toURI().toURL();
                        } catch (Exception e) {
                            log.error("Failed to load plugin class from external jar", e);
                            throw new RuntimeException(e);
                        }
                    })
                    .toArray(URL[]::new);

            ClassLoader contextClassLoader = PluginLoader.class.getClassLoader();
            URLClassLoader loader = new URLClassLoader(urls, contextClassLoader);
            ServiceLoader<T> serviceLoader = ServiceLoader.load(type, loader);

            List<T> extensions = new ArrayList<>();
            for (T impl : serviceLoader) {
                ClassLoader loaderOfImpl = impl.getClass().getClassLoader();

                // external only load
                if (loaderOfImpl != contextClassLoader) {
                    log.info("ExtensionJarLoader Loaded external module " + impl.getClass().getName());
                    extensions.add(impl);
                } else {
                    log.debug("Skipped internal module from external loader: " + impl.getClass().getName());
                }
            }
            return extensions;
        } catch (Exception e) {
            log.error("Failed to load plugin class from external jar", e);
            throw new RuntimeException("Failed to load extensions from " + directory, e);
        }
    }
}
