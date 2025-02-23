package org.idp.server.oauth.authentication;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Authentication implements Serializable {
  LocalDateTime time;
  List<String> methods = new ArrayList<>();
  List<String> acrValues = new ArrayList<>();

  public Authentication() {}

  public Authentication setTime(LocalDateTime time) {
    this.time = time;
    return this;
  }

  public Authentication addMethods(List<String> methods) {
    List<String> newValues = new ArrayList<>(this.methods);
    newValues.addAll(methods);
    this.methods = newValues;
    return this;
  }

  public Authentication addAcrValues(List<String> acrValues) {
    List<String> newValues = new ArrayList<>(this.methods);
    newValues.addAll(acrValues);
    this.acrValues = newValues;
    return this;
  }

  public LocalDateTime time() {
    return time;
  }

  public boolean hasAuthenticationTime() {
    return Objects.nonNull(time);
  }

  public List<String> methods() {
    return methods;
  }

  public boolean hasMethod() {
    return !methods.isEmpty();
  }

  public List<String> acrValues() {
    return acrValues;
  }

  public String toStringAcrValue() {
    return String.join(" ", acrValues);
  }

  public boolean hasAcrValues() {
    return !acrValues.isEmpty();
  }
}
