package com.zendo.identity.api;

import com.zendo.identity.domain.UserId;
import com.zendo.identity.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class IdentityQueryApiImpl implements IdentityQueryApi {

    private final UserRepository userRepository;

    public IdentityQueryApiImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserSummary> getUserById(String userId) {
        return userRepository.findById(new UserId(userId))
                .map(u -> new UserSummary(u.getId().value(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getStatus().name()));
    }

    @Override
    public Optional<UserSummary> getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(u -> new UserSummary(u.getId().value(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getStatus().name()));
    }
}
