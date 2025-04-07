package org.idp.server.core.basic.http;

import java.util.Iterator;
import java.util.List;

public class HttpRequestDynamicBodyKeys implements Iterable<String> {

  List<String> values;

  public HttpRequestDynamicBodyKeys() {}

  public HttpRequestDynamicBodyKeys(List<String> values) {
    this.values = values;
  }

  @Override
  public Iterator<String> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }
}
