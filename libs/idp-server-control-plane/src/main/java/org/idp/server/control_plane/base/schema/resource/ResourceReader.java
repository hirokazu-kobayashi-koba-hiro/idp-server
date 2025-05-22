/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
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
