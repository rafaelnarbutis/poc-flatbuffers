package com.bff.demo;

import com.bff.demo.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

@Component
public class Service {
    @Autowired
    private UserGateway userGateway;
    public Output getUser(String flow) {
        return flow.equalsIgnoreCase("flatc") ? getFlatc() : getJson();
    }
    private Output getFlatc() {
        final byte[] response = userGateway.getFlatc();

        final ByteBuffer buffer = ByteBuffer.wrap(response);

        User user = User.getRootAsUser(buffer);

        return new Output(user.name());
    }
    private Output getJson() {
        var response = userGateway.getJson();
        return new Output(response.name());
    }
    public record Output(String name){}
}
