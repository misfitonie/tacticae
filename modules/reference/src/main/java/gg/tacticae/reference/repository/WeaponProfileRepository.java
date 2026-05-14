package gg.tacticae.reference.repository;

import gg.tacticae.reference.domain.WeaponProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeaponProfileRepository extends JpaRepository<WeaponProfile, UUID> {
    Optional<WeaponProfile> findByBsdataId(String bsdataId);
    List<WeaponProfile> findByUnitId(UUID unitId);
}
