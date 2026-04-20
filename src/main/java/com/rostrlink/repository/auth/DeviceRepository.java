package com.rostrlink.repository.auth;

import com.rostrlink.entity.auth.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByUser_UserIdAndDeviceFingerprint(Long userId, String fingerprint);

    List<Device> findByUser_UserId(Long userId);
}
