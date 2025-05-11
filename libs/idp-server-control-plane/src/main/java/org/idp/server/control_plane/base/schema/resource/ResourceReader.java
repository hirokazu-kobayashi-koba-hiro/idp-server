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
