package gg.tacticae.reference.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "factions")
public class Faction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String bsdataId;

    @Column(nullable = false)
    private String name;

    protected Faction() {}

    public Faction(String bsdataId, String name) {
        this.bsdataId = bsdataId;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getBsdataId() { return bsdataId; }
    public String getName() { return name; }
}
