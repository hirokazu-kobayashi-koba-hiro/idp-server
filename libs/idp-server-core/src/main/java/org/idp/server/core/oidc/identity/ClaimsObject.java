package org.idp.server.core.oidc.identity;

import java.util.List;
import org.idp.server.basic.json.JsonReadable;

public class ClaimsObject implements JsonReadable {
  boolean essential;
  String value;
  List<String> values;

  public ClaimsObject() {}

  public boolean essential() {
    return essential;
  }

  public String value() {
    return value;
  }

  public List<String> values() {
    return values;
  }
}
