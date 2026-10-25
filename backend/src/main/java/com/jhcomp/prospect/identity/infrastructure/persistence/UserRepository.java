package com.jhcomp.prospect.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /** O e-mail e persistido normalizado em minusculas; normalizar antes de chamar. */
    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
