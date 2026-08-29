package com.zendo.security.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserCredentialsRepository extends JpaRepository<UserCredentialsJpaEntity, String> {
}
