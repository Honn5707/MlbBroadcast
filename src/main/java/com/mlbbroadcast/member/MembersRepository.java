package com.mlbbroadcast.member;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface MembersRepository extends JpaRepository<Members, Long> {

    //코인 조회/변경용: 비관적 쓰기 락. 대기 한도는 커넥션 설정 innodb_lock_wait_timeout(3초, application.yml)
    //한도 초과 시 PessimisticLockingFailureException
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT m FROM Members m WHERE m.id = :id")
    Optional<Members> findByIdForUpdate(@Param("id") Long id);


    Optional<Members> findMembersByProviderId(String providerId);

    List<Members> findByNickname(String nickname);

    boolean existsByNickname(String nickname);

    Optional<Members> findByProviderTypeAndProviderId(Provider providerType, String providerId);
}
