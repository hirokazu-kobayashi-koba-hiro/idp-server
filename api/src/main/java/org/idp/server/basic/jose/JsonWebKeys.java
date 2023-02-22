package org.idp.server.basic.jose;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** JsonWebKeys */
public class JsonWebKeys implements Iterable<JsonWebKey> {

  List<JsonWebKey> values;

  public JsonWebKeys() {
    this.values = new ArrayList<>();
  }

  public JsonWebKeys(List<JsonWebKey> values) {
    this.values = values;
  }

  @Override
  public Iterator<JsonWebKey> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return !values.isEmpty();
  }
}
