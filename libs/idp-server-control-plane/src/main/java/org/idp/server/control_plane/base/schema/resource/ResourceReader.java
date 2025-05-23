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

package org.idp.server.control_plane.base.schema.resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ResourceReader {

  public static String readClasspath(String path) {
    try (InputStream in = ResourceReader.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalArgumentException("Resource not found: " + path);
      }
      return new Scanner(in, StandardCharsets.UTF_8).useDelimiter("\\A").next();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read resource: " + path, e);
    }
  }
}
