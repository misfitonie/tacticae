package gg.tacticae.armies.parser;

import gg.tacticae.armies.domain.ArmyList;
import gg.tacticae.armies.domain.ParsedUnit;
import gg.tacticae.armies.domain.ParsedWeapon;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class RoszParser {

    public ArmyList parse(byte[] roszBytes) throws IOException {
        byte[] rosXml = extractRos(roszBytes);
        Document doc = parseXml(rosXml);

        Element root = doc.getDocumentElement();
        String rosterName = root.getAttribute("name");
        String factionName = "";
        List<ParsedUnit> units = new ArrayList<>();

        Element forces = child(root, "forces");
        if (forces != null) {
            List<Element> forceList = children(forces, "force");
            if (!forceList.isEmpty()) {
                Element force = forceList.get(0);
                factionName = force.getAttribute("catalogueName");
                Element selections = child(force, "selections");
                if (selections != null) {
                    for (Element sel : children(selections, "selection")) {
                        units.addAll(findUnits(sel));
                    }
                }
            }
        }

        return new ArmyList(rosterName, factionName, List.copyOf(units));
    }

    // -------------------------------------------------------------------------
    // Unit detection
    // -------------------------------------------------------------------------

    private List<ParsedUnit> findUnits(Element sel) {
        // Case 1 : profile Unit directement sur cette sélection
        Map<String, String> unitChars = profileChars(sel, "Unit");
        if (unitChars != null) {
            return List.of(buildUnit(sel.getAttribute("name"), intAttr(sel, "number", 1), unitChars, sel));
        }

        // Case 2 : sélection type="unit" dont le profil Unit est sur un modèle imbriqué
        // (pattern BattleScribe/NewRecruit pour les unités multi-modèles)
        if ("unit".equals(sel.getAttribute("type"))) {
            Map<String, String> childChars = findFirstUnitChars(sel);
            if (childChars != null) {
                return List.of(buildUnit(sel.getAttribute("name"), intAttr(sel, "number", 1), childChars, sel));
            }
        }

        // Case 3 : pas une unité à ce niveau, on descend
        List<ParsedUnit> units = new ArrayList<>();
        Element inner = child(sel, "selections");
        if (inner != null) {
            for (Element c : children(inner, "selection")) {
                units.addAll(findUnits(c));
            }
        }
        return units;
    }

    private ParsedUnit buildUnit(String name, int count, Map<String, String> chars, Element sel) {
        int t     = parseint(chars.get("T"), 4);
        int w     = parseint(chars.get("W"), 1);
        int sv    = threshold(chars.get("SV"), 7);
        int invSv = parseInvSave(chars);
        return new ParsedUnit(name, count, t, w, sv, invSv, List.copyOf(findWeapons(sel)));
    }

    /** Cherche en profondeur le premier profil Unit d'une sélection. */
    private Map<String, String> findFirstUnitChars(Element sel) {
        Map<String, String> chars = profileChars(sel, "Unit");
        if (chars != null) return chars;

        Element inner = child(sel, "selections");
        if (inner != null) {
            for (Element c : children(inner, "selection")) {
                Map<String, String> found = findFirstUnitChars(c);
                if (found != null) return found;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Weapon detection
    // -------------------------------------------------------------------------

    /** Collecte les armes dans une sélection et ses descendants non-unités. */
    private List<ParsedWeapon> findWeapons(Element sel) {
        List<ParsedWeapon> weapons = new ArrayList<>();
        Element inner = child(sel, "selections");
        if (inner == null) return weapons;

        for (Element c : children(inner, "selection")) {
            Map<String, String> chars = profileChars(c, "Ranged Weapons");
            if (chars != null) {
                weapons.add(buildWeapon(c, chars, false));
            } else {
                chars = profileChars(c, "Melee Weapons");
                if (chars != null) {
                    weapons.add(buildWeapon(c, chars, true));
                } else {
                    weapons.addAll(findWeapons(c));
                }
            }
        }
        return weapons;
    }

    private ParsedWeapon buildWeapon(Element sel, Map<String, String> chars, boolean isMelee) {
        String bsKey = isMelee ? "WS" : "BS";
        String range = isMelee ? "Melee" : chars.getOrDefault("RANGE", "-");
        return new ParsedWeapon(
            sel.getAttribute("name"),
            intAttr(sel, "number", 1),
            range,
            chars.getOrDefault("A", "1"),
            threshold(chars.get(bsKey), 4),
            parseint(chars.get("S"), 4),
            parseint(chars.get("AP"), 0),
            chars.getOrDefault("D", "1"),
            parseKeywords(chars.get("KEYWORDS"))
        );
    }

    // -------------------------------------------------------------------------
    // Profile helpers
    // -------------------------------------------------------------------------

    /**
     * Retourne les caractéristiques du premier profil de type {@code typeName}
     * qui est enfant DIRECT de sel/profiles (évite les profils des sous-sélections).
     * Les clés sont normalisées en majuscules pour absorber les variantes BSData/NR.
     */
    private Map<String, String> profileChars(Element sel, String typeName) {
        Element profiles = child(sel, "profiles");
        if (profiles == null) return null;
        for (Element profile : children(profiles, "profile")) {
            if (typeName.equals(profile.getAttribute("typeName"))) {
                return readCharacteristics(profile);
            }
        }
        return null;
    }

    private Map<String, String> readCharacteristics(Element profile) {
        Map<String, String> map = new LinkedHashMap<>();
        Element chars = child(profile, "characteristics");
        if (chars != null) {
            for (Element c : children(chars, "characteristic")) {
                // Normaliser en majuscules pour absorber "Sv"/"SV", "Keywords"/"KEYWORDS", etc.
                map.put(c.getAttribute("name").toUpperCase(), c.getTextContent().trim());
            }
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // DOM helpers
    // -------------------------------------------------------------------------

    private Element child(Element parent, String tag) {
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(((Element) n).getTagName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private List<Element> children(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(((Element) n).getTagName())) {
                result.add((Element) n);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Value parsers
    // -------------------------------------------------------------------------

    /** "3+" → 3, "3+/4++" → 3, "N/A" ou invalide → fallback */
    private int threshold(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        String digits = value.replaceAll("[^0-9].*", "").trim();
        if (digits.isEmpty()) return fallback;
        try { return Integer.parseInt(digits); }
        catch (NumberFormatException e) { return fallback; }
    }

    private int parseint(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private int intAttr(Element el, String attr, int fallback) {
        return parseint(el.getAttribute(attr), fallback);
    }

    private List<String> parseKeywords(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,;]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty() && !s.equals("-"))
            .toList();
    }

    private static final Pattern INV_PATTERN = Pattern.compile("(\\d+)\\+\\+");

    /** Extrait la save invulnérable (7 = aucune).
     *  Cherche dans les caractéristiques nommées INV, INVULNERABLE SAVE, etc.
     *  puis dans le champ SV au format "3+/4++". */
    private int parseInvSave(Map<String, String> chars) {
        for (String key : List.of("INV. SV.", "INV SV", "INV", "INVULNERABLE SAVE", "INVULNERABLE")) {
            String val = chars.get(key);
            if (val != null && !val.isBlank() && !val.equals("-") && !val.equals("N/A")) {
                int t = threshold(val, 7);
                if (t < 7) return t;
            }
        }
        String sv = chars.get("SV");
        if (sv != null) {
            Matcher m = INV_PATTERN.matcher(sv);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return 7;
    }

    // -------------------------------------------------------------------------
    // I/O
    // -------------------------------------------------------------------------

    private byte[] extractRos(byte[] zipBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".ros")) {
                    return zis.readAllBytes();
                }
                zis.closeEntry();
            }
        }
        throw new IOException("No .ros file found in .rosz archive");
    }

    private Document parseXml(byte[] xmlBytes) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // Protection XXE
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xmlBytes));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse roster XML", e);
        }
    }
}
