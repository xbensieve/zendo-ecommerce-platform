package com.zendo.security.application;

import java.util.Collection;

public interface TokenService {
    String generateToken(String userId, Collection<String> roles);
}
