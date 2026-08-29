package com.zendo.identity.domain;

import java.util.Optional;

public interface UserRepository {
    void save(User user);
    Optional<User> findById(UserId id);
    Optional<User> findByEmail(String email);
}
