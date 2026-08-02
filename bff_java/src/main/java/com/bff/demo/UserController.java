package com.bff.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final Service service;

    public UserController(Service service) {
        this.service = service;
    }

    @GetMapping("/users")
    public ResponseEntity<Service.Output> getUserAgeOnly(@RequestHeader(
            value = "flow_type",
            defaultValue = "json") String flow
    ){
        return ResponseEntity.ok(service.getUser(flow));
    }
}
