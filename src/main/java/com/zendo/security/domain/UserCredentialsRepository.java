package com.zendo.security.domain;

import java.util.Optional;

public interface UserCredentialsRepository {
    Optional<UserCredentials> findByUserId(String userId);
    void save(UserCredentials credentials);
}
