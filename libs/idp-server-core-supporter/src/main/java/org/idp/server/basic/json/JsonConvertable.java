package org.idp.server.basic.json;

public class JsonConvertable {
  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public static <TYPE> TYPE read(String value, Class<TYPE> typeClass) {
    return jsonConverter.read(value, typeClass);
  }

  public static String write(Object value) {
    return jsonConverter.write(value);
  }
}
