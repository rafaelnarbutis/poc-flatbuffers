package com.bff.demo;

import com.bff.demo.model.UserJson;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "userClient", url = "http://localhost:8081")
public interface UserGateway {
    @GetMapping(value = "/json", consumes = "application/json")
    UserJson getJson();
    @GetMapping(value = "/flatbuffers",  consumes = "application/x-flatbuffers")
    byte[] getFlatc();
}
