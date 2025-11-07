package org.idp.server.authentication.interactors.fido2;

import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class Fido2MetadataConfig implements JsonReadable {

  Map<String, Object> metadata;

  public Fido2MetadataConfig(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public String userIdParam() {
    return optValueAsString("user_id_param", "id");
  }

  public String optValueAsString(String key, String defaultValue) {
    if (metadata.containsKey(key)) {
      return metadata.get(key).toString();
    }
    return defaultValue;
  }
}
