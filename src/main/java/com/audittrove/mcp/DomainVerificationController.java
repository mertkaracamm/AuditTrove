package com.audittrove.mcp;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DomainVerificationController {
    @GetMapping(value = "/.well-known/openai-apps-challenge", produces = MediaType.TEXT_PLAIN_VALUE)
    public String challenge() {
        return "M06KfurR5CoeBTnr0Dx6ZMPF54dZ5cJ1bLENSR480SU";
    }
}