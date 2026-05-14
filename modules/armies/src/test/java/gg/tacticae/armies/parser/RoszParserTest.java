package gg.tacticae.armies.parser;

import gg.tacticae.armies.domain.ArmyList;
import gg.tacticae.armies.domain.ParsedUnit;
import gg.tacticae.armies.domain.ParsedWeapon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoszParserTest {

    private static final String SAMPLE_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <roster name="Test Army">
          <forces>
            <force catalogueName="Space Marines">
              <selections>
                <selection name="Intercessors" number="5">
                  <profiles>
                    <profile typeName="Unit">
                      <characteristics>
                        <characteristic name="T">4</characteristic>
                        <characteristic name="Sv">3+</characteristic>
                        <characteristic name="W">2</characteristic>
                      </characteristics>
                    </profile>
                  </profiles>
                  <selections>
                    <selection name="Bolt Rifle" number="5">
                      <profiles>
                        <profile typeName="Ranged Weapons">
                          <characteristics>
                            <characteristic name="A">2</characteristic>
                            <characteristic name="BS">3+</characteristic>
                            <characteristic name="S">4</characteristic>
                            <characteristic name="AP">-1</characteristic>
                            <characteristic name="D">1</characteristic>
                            <characteristic name="Keywords">ASSAULT</characteristic>
                          </characteristics>
                        </profile>
                      </profiles>
                    </selection>
                    <selection name="Astartes Chainsword" number="1">
                      <profiles>
                        <profile typeName="Melee Weapons">
                          <characteristics>
                            <characteristic name="A">5</characteristic>
                            <characteristic name="WS">3+</characteristic>
                            <characteristic name="S">4</characteristic>
                            <characteristic name="AP">-1</characteristic>
                            <characteristic name="D">1</characteristic>
                            <characteristic name="Keywords">LETHAL HITS</characteristic>
                          </characteristics>
                        </profile>
                      </profiles>
                    </selection>
                  </selections>
                </selection>
                <selection name="Devastator Squad" number="5">
                  <profiles>
                    <profile typeName="Unit">
                      <characteristics>
                        <characteristic name="T">4</characteristic>
                        <characteristic name="Sv">3+</characteristic>
                        <characteristic name="W">2</characteristic>
                      </characteristics>
                    </profile>
                  </profiles>
                  <selections>
                    <selection name="Multi-melta" number="2">
                      <profiles>
                        <profile typeName="Ranged Weapons">
                          <characteristics>
                            <characteristic name="A">2</characteristic>
                            <characteristic name="BS">3+</characteristic>
                            <characteristic name="S">9</characteristic>
                            <characteristic name="AP">-4</characteristic>
                            <characteristic name="D">D6+2</characteristic>
                            <characteristic name="Keywords">MELTA 4</characteristic>
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
        @DisplayName("extrait les deux unités")
        void parsesTwoUnits() throws IOException {
            ArmyList army = parser.parse(rosz(SAMPLE_XML));
            assertThat(army.units()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Unit parsing")
    class UnitLevel {

        @Test
        @DisplayName("extrait les stats d'unité (T/W/Sv)")
        void parsesUnitStats() throws IOException {
            ParsedUnit unit = parser.parse(rosz(SAMPLE_XML)).units().get(0);
            assertThat(unit.name()).isEqualTo("Intercessors");
            assertThat(unit.count()).isEqualTo(5);
            assertThat(unit.toughness()).isEqualTo(4);
            assertThat(unit.wounds()).isEqualTo(2);
            assertThat(unit.save()).isEqualTo(3);
        }

        @Test
        @DisplayName("extrait les deux armes de l'unité")
        void parsesTwoWeapons() throws IOException {
            ParsedUnit unit = parser.parse(rosz(SAMPLE_XML)).units().get(0);
            assertThat(unit.weapons()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Weapon parsing")
    class WeaponLevel {

        @Test
        @DisplayName("arme à distance — A/BS/S/AP/D/keywords")
        void parsesRangedWeapon() throws IOException {
            ParsedWeapon w = parser.parse(rosz(SAMPLE_XML)).units().get(0).weapons().get(0);
            assertThat(w.name()).isEqualTo("Bolt Rifle");
            assertThat(w.count()).isEqualTo(5);
            assertThat(w.attacks()).isEqualTo("2");
            assertThat(w.skill()).isEqualTo(3);
            assertThat(w.strength()).isEqualTo(4);
            assertThat(w.ap()).isEqualTo(-1);
            assertThat(w.damage()).isEqualTo("1");
            assertThat(w.keywords()).containsExactly("ASSAULT");
        }

        @Test
        @DisplayName("arme de mêlée — WS reconnu comme skill")
        void parsesMeleeWeapon() throws IOException {
            ParsedWeapon w = parser.parse(rosz(SAMPLE_XML)).units().get(0).weapons().get(1);
            assertThat(w.name()).isEqualTo("Astartes Chainsword");
            assertThat(w.skill()).isEqualTo(3);
            assertThat(w.keywords()).containsExactly("LETHAL HITS");
        }

        @Test
        @DisplayName("damage dé (D6+2) conservé tel quel")
        void preservesDiceDamage() throws IOException {
            ParsedWeapon w = parser.parse(rosz(SAMPLE_XML)).units().get(1).weapons().get(0);
            assertThat(w.damage()).isEqualTo("D6+2");
            assertThat(w.ap()).isEqualTo(-4);
        }
    }

    @Test
    @DisplayName("lève IOException si le ZIP ne contient pas de .ros")
    void throwsOnMissingRos() {
        assertThatThrownBy(() -> parser.parse(emptyZip()))
            .isInstanceOf(IOException.class)
            .hasMessageContaining(".ros");
    }

    // --- helpers ---

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
}
