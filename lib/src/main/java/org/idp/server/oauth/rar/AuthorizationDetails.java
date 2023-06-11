package org.idp.server.oauth.rar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AuthorizationDetails implements Iterable<AuthorizationDetail> {

  List<AuthorizationDetail> values;

  public AuthorizationDetails() {
    this.values = new ArrayList<>();
  }

  public AuthorizationDetails(List<AuthorizationDetail> values) {
    this.values = values;
  }

  @Override
  public Iterator<AuthorizationDetail> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public List<Map<String, Object>> values() {
    return values.stream().map(AuthorizationDetail::values).toList();
  }
}
