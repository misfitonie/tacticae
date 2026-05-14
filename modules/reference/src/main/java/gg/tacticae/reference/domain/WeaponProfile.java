package gg.tacticae.reference.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "weapon_profiles")
public class WeaponProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String bsdataId;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private UnitProfile unit;

    /** Nombre d'attaques — peut être une valeur fixe ("10") ou un dé ("D6", "2D3"). */
    @Column(nullable = false)
    private String attacks;

    /** Seuil de touche (BS/CC), ex: 3 pour 3+. */
    private int skill;

    private int strength;

    /** Pénétration d'armure, valeur absolue négative, ex: -2 pour AP-2. */
    private int ap;

    /** Dégâts — peut être fixe ("2") ou un dé ("D3"). */
    @Column(nullable = false)
    private String damage;

    /** Keywords séparés par des virgules, ex: "HEAVY,LETHAL HITS". */
    @Column(columnDefinition = "TEXT")
    private String keywords;

    protected WeaponProfile() {}

    public WeaponProfile(String bsdataId, String name, UnitProfile unit,
                         String attacks, int skill, int strength, int ap,
                         String damage, String keywords) {
        this.bsdataId = bsdataId;
        this.name = name;
        this.unit = unit;
        this.attacks = attacks;
        this.skill = skill;
        this.strength = strength;
        this.ap = ap;
        this.damage = damage;
        this.keywords = keywords;
    }

    public UUID getId() { return id; }
    public String getBsdataId() { return bsdataId; }
    public String getName() { return name; }
    public UnitProfile getUnit() { return unit; }
    public String getAttacks() { return attacks; }
    public int getSkill() { return skill; }
    public int getStrength() { return strength; }
    public int getAp() { return ap; }
    public String getDamage() { return damage; }
    public String getKeywords() { return keywords; }
}
