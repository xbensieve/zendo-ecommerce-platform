package com.zendo.identity.infrastructure.persistence;

import com.zendo.identity.domain.User;
import com.zendo.identity.domain.UserId;
import com.zendo.identity.domain.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final SpringDataUserRepository jpaRepository;

    public UserRepositoryImpl(SpringDataUserRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(User user) {
        jpaRepository.saveAndFlush(toJpaEntity(user));
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    private UserJpaEntity toJpaEntity(User user) {
        return new UserJpaEntity(
                user.getId().value(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }

    private User toDomain(UserJpaEntity entity) {
        return new User(
                new UserId(entity.getId()),
                entity.getEmail(),
                entity.getFirstName(),
                entity.getLastName(),
                com.zendo.identity.domain.UserStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt()
        );
    }
}
