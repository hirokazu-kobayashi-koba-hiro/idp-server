package org.idp.server.core.basic.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** ResourceReadable */
public interface ResourceReadable {

  default String read(String path) throws IOException {
    Path file = Paths.get(path);
    return Files.readString(file);
  }
}
