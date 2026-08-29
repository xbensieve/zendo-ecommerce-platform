package com.zendo.identity.api;

import com.zendo.identity.domain.User;
import com.zendo.identity.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityCommandApiImpl implements IdentityCommandApi {

    private final UserRepository userRepository;

    public IdentityCommandApiImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY) // Must be called within an existing transaction (e.g., from RegistrationUseCases)
    public String createUser(String email, String firstName, String lastName) {
        // Uniqueness is enforced by the database schema (email UNIQUE constraint).
        // A DataIntegrityViolationException will be thrown if it already exists.
        User user = User.createNew(email, firstName, lastName);
        userRepository.save(user);
        return user.getId().value();
    }
}
