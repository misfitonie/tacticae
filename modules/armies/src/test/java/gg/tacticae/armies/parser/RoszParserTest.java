package gg.tacticae.armies.parser;

import gg.tacticae.armies.domain.ArmyList;
import gg.tacticae.armies.domain.ParsedUnit;
import gg.tacticae.armies.domain.ParsedWeapon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoszParserTest {

    // XML synthétique qui reproduit le format BSData/NewRecruit réel :
    // - namespace xmlns, attributs id/typeId/hidden, éléments costs/categories/rules
    // - noms de caractéristiques en majuscules (SV, not Sv)
    // - unité dont le profil Unit est sur un modèle imbriqué (Sister Superior pattern)
    private static final String SAMPLE_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <roster id="abc" name="Test Army" xmlns="http://www.battlescribe.net/schema/rosterSchema">
          <costs><cost name="pts" typeId="points" value="1000"/></costs>
          <forces>
            <force id="f1" catalogueName="Space Marines" catalogueRevision="100">
              <publications/>
              <categories/>
              <rules/>
              <selections>

                <!-- Sélection de configuration — doit être ignorée -->
                <selection id="s0" name="Battle Size" entryId="e0" number="1" type="upgrade">
                  <selections>
                    <selection id="s01" name="Strike Force" entryId="e01" number="1" type="upgrade"/>
                  </selections>
                </selection>

                <!-- Unité avec profil Unit DIRECT (pattern simple) -->
                <selection id="s1" name="Intercessors" entryId="e1" number="1" type="unit">
                  <rules/>
                  <profiles>
                    <profile id="p1" name="Intercessor Squad" hidden="false" typeId="t1" typeName="Abilities">
                      <characteristics>
                        <characteristic name="Description" typeId="d1">Some ability</characteristic>
                      </characteristics>
                    </profile>
                    <profile id="p2" name="Intercessor" hidden="false" typeId="t2" typeName="Unit">
                      <characteristics>
                        <characteristic name="M" typeId="m1">6"</characteristic>
                        <characteristic name="T" typeId="t1">4</characteristic>
                        <characteristic name="SV" typeId="sv1">3+</characteristic>
                        <characteristic name="W" typeId="w1">2</characteristic>
                        <characteristic name="LD" typeId="ld1">6+</characteristic>
                        <characteristic name="OC" typeId="oc1">2</characteristic>
                      </characteristics>
                    </profile>
                  </profiles>
                  <categories/>
                  <selections>
                    <selection id="s2" name="Bolt Rifle" entryId="e2" number="10" type="upgrade">
                      <profiles>
                        <profile id="p3" name="Bolt Rifle" hidden="false" typeId="t3" typeName="Ranged Weapons">
                          <characteristics>
                            <characteristic name="Range" typeId="r1">24"</characteristic>
                            <characteristic name="A" typeId="a1">2</characteristic>
                            <characteristic name="BS" typeId="bs1">3+</characteristic>
                            <characteristic name="S" typeId="s1">4</characteristic>
                            <characteristic name="AP" typeId="ap1">-1</characteristic>
                            <characteristic name="D" typeId="d1">1</characteristic>
                            <characteristic name="Keywords" typeId="kw1">ASSAULT</characteristic>
                          </characteristics>
                        </profile>
                      </profiles>
                      <costs><cost name="pts" typeId="points" value="0"/></costs>
                    </selection>
                    <selection id="s3" name="Astartes Chainsword" entryId="e3" number="1" type="upgrade">
                      <profiles>
                        <profile id="p4" name="Astartes Chainsword" hidden="false" typeId="t4" typeName="Melee Weapons">
                          <characteristics>
                            <characteristic name="Range" typeId="r1">Melee</characteristic>
                            <characteristic name="A" typeId="a1">5</characteristic>
                            <characteristic name="WS" typeId="ws1">3+</characteristic>
                            <characteristic name="S" typeId="s1">4</characteristic>
                            <characteristic name="AP" typeId="ap1">-1</characteristic>
                            <characteristic name="D" typeId="d1">1</characteristic>
                            <characteristic name="Keywords" typeId="kw1">-</characteristic>
                          </characteristics>
                        </profile>
                      </profiles>
                    </selection>
                  </selections>
                  <costs><cost name="pts" typeId="points" value="200"/></costs>
                </selection>

                <!-- Unité multi-modèles (pattern Battle Sisters) :
                     profil Unit sur les modèles imbriqués, pas sur l'unité elle-même -->
                <selection id="s4" name="Devastator Squad" entryId="e4" number="1" type="unit">
                  <profiles>
                    <profile id="p5" name="Defenders" hidden="false" typeId="t5" typeName="Abilities">
                      <characteristics>
                        <characteristic name="Description" typeId="d1">Ability text</characteristic>
                      </characteristics>
                    </profile>
                  </profiles>
                  <selections>
                    <selection id="s5" name="Devastator Sergeant" entryId="e5" number="1" type="model">
                      <profiles>
                        <profile id="p6" name="Devastator Marine" hidden="false" typeId="t6" typeName="Unit">
                          <characteristics>
                            <characteristic name="M" typeId="m1">6"</characteristic>
                            <characteristic name="T" typeId="t1">4</characteristic>
                            <characteristic name="SV" typeId="sv1">3+</characteristic>
                            <characteristic name="W" typeId="w1">2</characteristic>
                          </characteristics>
                        </profile>
                      </profiles>
                      <selections>
                        <selection id="s6" name="Bolt Pistol" entryId="e6" number="1" type="upgrade">
                          <profiles>
                            <profile id="p7" name="Bolt Pistol" hidden="false" typeId="t7" typeName="Ranged Weapons">
                              <characteristics>
                                <characteristic name="A" typeId="a1">1</characteristic>
                                <characteristic name="BS" typeId="bs1">3+</characteristic>
                                <characteristic name="S" typeId="s1">4</characteristic>
                                <characteristic name="AP" typeId="ap1">0</characteristic>
                                <characteristic name="D" typeId="d1">1</characteristic>
                                <characteristic name="Keywords" typeId="kw1">PISTOL</characteristic>
                              </characteristics>
                            </profile>
                          </profiles>
                        </selection>
                      </selections>
                    </selection>
                    <selection id="s7" name="Multi-melta" entryId="e7" number="2" type="upgrade">
                      <profiles>
                        <profile id="p8" name="Multi-melta" hidden="false" typeId="t8" typeName="Ranged Weapons">
                          <characteristics>
                            <characteristic name="A" typeId="a1">2</characteristic>
                            <characteristic name="BS" typeId="bs1">3+</characteristic>
                            <characteristic name="S" typeId="s1">9</characteristic>
                            <characteristic name="AP" typeId="ap1">-4</characteristic>
                            <characteristic name="D" typeId="d1">D6+2</characteristic>
                            <characteristic name="Keywords" typeId="kw1">MELTA 4</characteristic>
                          </characteristics>
                        </profile>
                      </profiles>
                    </selection>
                  </selections>
                </selection>

              </selections>
            </force>
          </forces>
        </roster>
        """;

    private final RoszParser parser = new RoszParser();

    @Nested
    @DisplayName("Army-level parsing")
    class ArmyLevel {

        @Test
        @DisplayName("extrait le nom de la liste et la faction")
        void parsesNameAndFaction() throws IOException {
            ArmyList army = parser.parse(rosz(SAMPLE_XML));
            assertThat(army.name()).isEqualTo("Test Army");
            assertThat(army.factionName()).isEqualTo("Space Marines");
        }

        @Test
        @DisplayName("ignore les sélections de configuration (Battle Size etc.)")
        void ignoresConfigurationSelections() throws IOException {
            ArmyList army = parser.parse(rosz(SAMPLE_XML));
            assertThat(army.units()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Unit parsing — profil direct")
    class UnitDirect {

        @Test
        @DisplayName("extrait T/W/Sv depuis le profil Unit")
        void parsesUnitStats() throws IOException {
            ParsedUnit unit = parser.parse(rosz(SAMPLE_XML)).units().get(0);
            assertThat(unit.name()).isEqualTo("Intercessors");
            assertThat(unit.toughness()).isEqualTo(4);
            assertThat(unit.wounds()).isEqualTo(2);
            assertThat(unit.save()).isEqualTo(3);
        }

        @Test
        @DisplayName("ignore les profils Abilities sur la même sélection")
        void ignoresAbilitiesProfiles() throws IOException {
            ParsedUnit unit = parser.parse(rosz(SAMPLE_XML)).units().get(0);
            assertThat(unit.toughness()).isEqualTo(4); // stats correctes, pas mélangées
        }

        @Test
        @DisplayName("deux armes trouvées (Bolt Rifle + Astartes Chainsword)")
        void findsBothWeapons() throws IOException {
            ParsedUnit unit = parser.parse(rosz(SAMPLE_XML)).units().get(0);
            assertThat(unit.weapons()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Unit parsing — profil sur modèle imbriqué (pattern BSData)")
    class UnitNested {

        @Test
        @DisplayName("utilise le nom de l'unité parente, pas du modèle")
        void usesParentUnitName() throws IOException {
            ParsedUnit unit = parser.parse(rosz(SAMPLE_XML)).units().get(1);
            assertThat(unit.name()).isEqualTo("Devastator Squad");
        }

        @Test
        @DisplayName("récupère les stats du premier modèle trouvé")
        void getsStatsFromFirstModel() throws IOException {
            ParsedUnit unit = parser.parse(rosz(SAMPLE_XML)).units().get(1);
            assertThat(unit.toughness()).isEqualTo(4);
            assertThat(unit.save()).isEqualTo(3);
        }

        @Test
        @DisplayName("collecte les armes de tous les modèles imbriqués")
        void collectsWeaponsFromAllModels() throws IOException {
            ParsedUnit unit = parser.parse(rosz(SAMPLE_XML)).units().get(1);
            // Bolt Pistol (depuis Sergeant) + Multi-melta (direct)
            assertThat(unit.weapons()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Weapon parsing")
    class WeaponLevel {

        @Test
        @DisplayName("arme à distance — tous les champs")
        void parsesRangedWeapon() throws IOException {
            ParsedWeapon w = parser.parse(rosz(SAMPLE_XML)).units().get(0).weapons().get(0);
            assertThat(w.name()).isEqualTo("Bolt Rifle");
            assertThat(w.count()).isEqualTo(10);
            assertThat(w.attacks()).isEqualTo("2");
            assertThat(w.skill()).isEqualTo(3);
            assertThat(w.strength()).isEqualTo(4);
            assertThat(w.ap()).isEqualTo(-1);
            assertThat(w.damage()).isEqualTo("1");
            assertThat(w.keywords()).containsExactly("ASSAULT");
        }

        @Test
        @DisplayName("arme de mêlée — WS reconnu comme skill, keyword '-' ignoré")
        void parsesMeleeWeaponAndFiltersEmptyKeyword() throws IOException {
            ParsedWeapon w = parser.parse(rosz(SAMPLE_XML)).units().get(0).weapons().get(1);
            assertThat(w.name()).isEqualTo("Astartes Chainsword");
            assertThat(w.skill()).isEqualTo(3);
            assertThat(w.keywords()).isEmpty(); // "-" filtré
        }

        @Test
        @DisplayName("damage variable (D6+2) conservé tel quel")
        void preservesDiceDamage() throws IOException {
            // Multi-melta dans Devastator Squad
            ParsedWeapon w = parser.parse(rosz(SAMPLE_XML)).units().get(1).weapons().stream()
                .filter(pw -> pw.name().equals("Multi-melta")).findFirst().orElseThrow();
            assertThat(w.damage()).isEqualTo("D6+2");
            assertThat(w.ap()).isEqualTo(-4);
        }
    }

    @Nested
    @DisplayName("Fichier réel AoF.rosz")
    class RealFile {

        @Test
        @DisplayName("parse sans exception et produit une liste cohérente")
        void parsesRealFileWithoutErrors() throws IOException {
            byte[] roszBytes = readResource("/AoF.rosz");
            ArmyList army = parser.parse(roszBytes);

            assertThat(army.name()).isEqualTo("AoF");
            assertThat(army.factionName()).contains("Sororitas");
            assertThat(army.units()).isNotEmpty();

            // Toutes les unités ont des stats sensées
            for (var unit : army.units()) {
                assertThat(unit.name()).isNotBlank();
                assertThat(unit.toughness()).isBetween(1, 14);
                assertThat(unit.wounds()).isBetween(1, 50);
                assertThat(unit.save()).isBetween(2, 7);
            }

            // Debug : affiche le résultat pour inspection manuelle
            System.out.println("=== AoF.rosz — " + army.factionName() + " ===");
            for (var unit : army.units()) {
                System.out.printf("  %-40s T%d W%d Sv%d+%n", unit.name(), unit.toughness(), unit.wounds(), unit.save());
                for (var w : unit.weapons()) {
                    System.out.printf("    %-35s A%-4s BS%d+ S%-2d AP%-3d D%s [%s]%n",
                        w.name(), w.attacks(), w.skill(), w.strength(), w.ap(), w.damage(),
                        String.join(", ", w.keywords()));
                }
            }
        }

        @Test
        @DisplayName("toutes les unités connues de la liste sont présentes")
        void containsExpectedUnits() throws IOException {
            byte[] roszBytes = readResource("/AoF.rosz");
            ArmyList army = parser.parse(roszBytes);
            List<String> names = army.units().stream().map(u -> u.name()).toList();

            assertThat(names).contains(
                "Aestred Thurga and Agathae Dolan",
                "Battle Sisters Squad",
                "Retributor Squad"
            );
        }
    }

    @Test
    @DisplayName("lève IOException si le ZIP ne contient pas de .ros")
    void throwsOnMissingRos() {
        assertThatThrownBy(() -> parser.parse(emptyZip()))
            .isInstanceOf(IOException.class)
            .hasMessageContaining(".ros");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private byte[] rosz(String rosXml) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("army.ros"));
            zos.write(rosXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] emptyZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("readme.txt"));
            zos.write("nothing".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] readResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Resource not found: " + path);
            return is.readAllBytes();
        }
    }
}
