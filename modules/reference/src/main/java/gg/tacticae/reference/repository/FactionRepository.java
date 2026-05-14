package gg.tacticae.reference.repository;

import gg.tacticae.reference.domain.Faction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FactionRepository extends JpaRepository<Faction, UUID> {
    Optional<Faction> findByBsdataId(String bsdataId);
}
