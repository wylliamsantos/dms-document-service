package br.com.dms.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SwaggerRedirectController {

    private static final String SWAGGER_UI_PATH = "/swagger-ui/index.html";

    @GetMapping("/")
    public String redirectRoot() {
        return "redirect:" + SWAGGER_UI_PATH;
    }
}
