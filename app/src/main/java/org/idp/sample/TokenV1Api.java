package org.idp.sample;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("{tenant-id}/api/v1/tokens")
public class TokenV1Api {

  @PostMapping
  public ResponseEntity<?> request(HttpRequest httpRequest) {
    HttpHeaders headers = httpRequest.getHeaders();
    Map<String, String> singleValueMap = headers.toSingleValueMap();

    return new ResponseEntity<>(HttpStatus.OK);
  }
}
