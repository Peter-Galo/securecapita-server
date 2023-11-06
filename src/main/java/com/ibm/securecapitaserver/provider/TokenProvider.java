package com.ibm.securecapitaserver.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

public class TokenProvider {

    @Value("${jwt.secret}")
    private String secret;
}
