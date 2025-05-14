package org.idp.server.basic.json.schema.format;

public class NoActionFormater implements JsonPropertyFormater {

  @Override
  public boolean match(String value) {
    return true;
  }
}
