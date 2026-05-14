package gg.tacticae.reference.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "unit_profiles")
public class UnitProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String bsdataId;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "faction_id")
    private Faction faction;

    private int toughness;
    private int wounds;
    private int save;

    protected UnitProfile() {}

    public UnitProfile(String bsdataId, String name, Faction faction, int toughness, int wounds, int save) {
        this.bsdataId = bsdataId;
        this.name = name;
        this.faction = faction;
        this.toughness = toughness;
        this.wounds = wounds;
        this.save = save;
    }

    public UUID getId() { return id; }
    public String getBsdataId() { return bsdataId; }
    public String getName() { return name; }
    public Faction getFaction() { return faction; }
    public int getToughness() { return toughness; }
    public int getWounds() { return wounds; }
    public int getSave() { return save; }
}
