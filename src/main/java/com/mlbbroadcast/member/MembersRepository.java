package com.mlbbroadcast.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface MembersRepository extends JpaRepository<Members, Long> {


    Optional<Members> findMembersByProviderId(String providerId);

    List<Members> findByNickname(String nickname);

    boolean existsByNickname(String nickname);

    Optional<Members> findByProviderTypeAndProviderId(Provider providerType, String providerId);
}
