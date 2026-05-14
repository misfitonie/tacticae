package gg.tacticae.reference.repository;

import gg.tacticae.reference.domain.UnitProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitProfileRepository extends JpaRepository<UnitProfile, UUID> {
    Optional<UnitProfile> findByBsdataId(String bsdataId);
    List<UnitProfile> findByFactionId(UUID factionId);
}
