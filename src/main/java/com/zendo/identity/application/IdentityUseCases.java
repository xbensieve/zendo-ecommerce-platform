package com.zendo.identity.application;

import com.zendo.identity.domain.User;
import com.zendo.identity.domain.UserId;
import com.zendo.identity.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IdentityUseCases {

    private final UserRepository userRepository;

    public IdentityUseCases(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void createUser(String email, String firstName, String lastName) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("User with email already exists");
        }
        User user = User.createNew(email, firstName, lastName);
        userRepository.save(user);
    }

    public void suspendUser(String userId) {
        User user = userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.suspend();
        userRepository.save(user);
    }

    public void reactivateUser(String userId) {
        User user = userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.reactivate();
        userRepository.save(user);
    }

    public void deactivateUser(String userId) {
        User user = userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.deactivate();
        userRepository.save(user);
    }
}
