package org.idp.server.basic.json.schema.format;

import java.net.URI;
import java.net.URISyntaxException;

public class UriFormater implements JsonPropertyFormater {

  @Override
  public boolean match(String value) {
    try {
      new URI(value);
      return true;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
