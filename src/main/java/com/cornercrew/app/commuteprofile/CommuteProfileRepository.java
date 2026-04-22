package com.cornercrew.app.commuteprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommuteProfileRepository extends JpaRepository<CommuteProfile, Long> {

    Optional<CommuteProfile> findByDriverId(Long driverId);

    void deleteByDriverId(Long driverId);
}
