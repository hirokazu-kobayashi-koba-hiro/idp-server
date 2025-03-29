package org.idp.server.core.oauth.identity;

import org.idp.server.core.basic.json.JsonReadable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Address implements JsonReadable, Serializable {

  String formatted;
  String streetAddress;
  String locality;
  String region;
  String postalCode;
  String country;

  public Address() {}

  public String formatted() {
    return formatted;
  }

  public Address setFormatted(String formatted) {
    this.formatted = formatted;
    return this;
  }

  public String streetAddress() {
    return streetAddress;
  }

  public Address setStreetAddress(String streetAddress) {
    this.streetAddress = streetAddress;
    return this;
  }

  public String locality() {
    return locality;
  }

  public Address setLocality(String locality) {
    this.locality = locality;
    return this;
  }

  public String region() {
    return region;
  }

  public Address setRegion(String region) {
    this.region = region;
    return this;
  }

  public String postalCode() {
    return postalCode;
  }

  public Address setPostalCode(String postalCode) {
    this.postalCode = postalCode;
    return this;
  }

  public String country() {
    return country;
  }

  public Address setCountry(String country) {
    this.country = country;
    return this;
  }

  public boolean exists() {
    return hasFormatted() || hasStreetAddress() || hasLocality() || hasRegion() || hasPostalCode() || hasCountry();
  }

  public boolean hasFormatted() {
    return formatted != null && !formatted.isEmpty();
  }
  public boolean hasStreetAddress() {
    return streetAddress != null && !streetAddress.isEmpty();
  }

  public boolean hasLocality() {
    return locality != null && !locality.isEmpty();
  }

  public boolean hasRegion() {
    return region != null && !region.isEmpty();
  }

  public boolean hasPostalCode() {
    return postalCode != null && !postalCode.isEmpty();
  }

  public boolean hasCountry() {
    return country != null && !country.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (hasFormatted()) map.put("formatted", formatted);
    if (hasStreetAddress()) map.put("street_address", streetAddress);
    if (hasLocality()) map.put("locality", locality);
    if (hasRegion()) map.put("region", region);
    if (hasPostalCode()) map.put("postal_code", postalCode);
    if (hasCountry()) map.put("country", country);
    return map;
  }
}
