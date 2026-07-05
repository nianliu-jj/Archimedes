package io.github.nianliu.archimedes.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }

    @Deprecated
    @GetMapping("/legacy-ping")
    public String legacyPing() {
        return "pong";
    }
}
