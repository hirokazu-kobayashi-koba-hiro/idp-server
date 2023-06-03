package org.idp.server.oauth.identity;

import java.util.HashMap;
import java.util.Map;

public class Address {
  String formatted;
  String streetAddress;
  String locality;
  String region;
  String postalCode;
  String country;
  Map<String, Object> values = new HashMap<>();

  public Address() {}

  public String formatted() {
    return formatted;
  }

  public Address setFormatted(String formatted) {
    this.formatted = formatted;
    this.values.put("formatted", formatted);
    return this;
  }

  public String streetAddress() {
    return streetAddress;
  }

  public Address setStreetAddress(String streetAddress) {
    this.streetAddress = streetAddress;
    this.values.put("street_address", streetAddress);
    return this;
  }

  public String locality() {
    return locality;
  }

  public Address setLocality(String locality) {
    this.locality = locality;
    this.values.put("locality", locality);
    return this;
  }

  public String region() {
    return region;
  }

  public Address setRegion(String region) {
    this.region = region;
    this.values.put("region", region);
    return this;
  }

  public String postalCode() {
    return postalCode;
  }

  public Address setPostalCode(String postalCode) {
    this.postalCode = postalCode;
    this.values.put("postal_code", postalCode);
    return this;
  }

  public String country() {
    return country;
  }

  public Address setCountry(String country) {
    this.country = country;
    this.values.put("country", country);
    return this;
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public Map<String, Object> values() {
    return values;
  }
}
