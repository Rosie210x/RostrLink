package com.rostrlink.repository.auth;

import com.rostrlink.entity.auth.NfcTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface NfcTagRepository extends JpaRepository<NfcTag, Long> {

    Optional<NfcTag> findByTagCodeAndRevokedAtIsNull(String tagCode);

    List<NfcTag> findByUser_UserId(Long userId);
}