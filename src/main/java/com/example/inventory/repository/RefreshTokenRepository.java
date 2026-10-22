package com.example.inventory.repository;

import com.example.inventory.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** 리프레시 토큰 해시 저장. 원문 토큰은 저장하지 않는다. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** 해시가 일치하는 저장 행을 찾는다. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 아직 폐기되지 않은 행만 revoked_at을 기록한다.
     * 동시에 같은 토큰으로 재발급이 들어오면 한 건만 1을 반환한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken r set r.revokedAt = :revokedAt where r.id = :id and r.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt);
}
