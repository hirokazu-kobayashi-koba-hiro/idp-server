package org.idp.server.basic.json.schema.format;

public enum JsonPropertyFormat {
  UUID(new UuidFormater()),
  URI(new UriFormater()),
  UNDEFINED(new NoActionFormater());

  JsonPropertyFormater formater;

  JsonPropertyFormat(JsonPropertyFormater formater) {
    this.formater = formater;
  }

  public static JsonPropertyFormat of(String name) {
    for (JsonPropertyFormat format : JsonPropertyFormat.values()) {
      if (format.name().equalsIgnoreCase(name)) {
        return format;
      }
    }
    return UNDEFINED;
  }

  public boolean match(String target) {
    return formater.match(target);
  }
}
