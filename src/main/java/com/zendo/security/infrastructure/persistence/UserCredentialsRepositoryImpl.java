package com.zendo.security.infrastructure.persistence;

import com.zendo.security.domain.Role;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserCredentialsRepositoryImpl implements UserCredentialsRepository {

    private final SpringDataUserCredentialsRepository jpaRepository;

    public UserCredentialsRepositoryImpl(SpringDataUserCredentialsRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<UserCredentials> findByUserId(String userId) {
        return jpaRepository.findById(userId)
                .map(entity -> new UserCredentials(entity.getUserId(), entity.getPasswordHash(), Role.valueOf(entity.getRole())));
    }

    @Override
    public void save(UserCredentials credentials) {
        jpaRepository.saveAndFlush(new UserCredentialsJpaEntity(credentials.getUserId(), credentials.getPasswordHash(), credentials.getRole().name()));
    }
}
