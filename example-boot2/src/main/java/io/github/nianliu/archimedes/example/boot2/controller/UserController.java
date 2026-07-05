package io.github.nianliu.archimedes.example.boot2.controller;

import io.github.nianliu.archimedes.example.boot2.model.CreateUserRequest;
import io.github.nianliu.archimedes.example.boot2.model.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping
    public List<UserResponse> listUsers(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return Arrays.asList(
                new UserResponse(1L, "alice", "alice@example.com"),
                new UserResponse(2L, "bob", "bob@example.com")
        );
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return new UserResponse(id, "user-" + id, "user-" + id + "@example.com");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@RequestBody CreateUserRequest request) {
        return new UserResponse(100L, request.getUsername(), request.getEmail());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        // demo endpoint for Archimedes REST API scanning
    }
}
