package org.idp.server.adapters.springboot.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {
  @GetMapping("/{path:[^\\.]*}")
  public String forward() {
    return "forward:/index.html";
  }
}
