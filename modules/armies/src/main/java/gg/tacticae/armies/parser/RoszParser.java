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

    // Recursively finds selections that have a "Unit" profile.
    // Stops recursing once a unit is found (its child selections are weapons).
    private List<ParsedUnit> findUnits(Element sel) {
        Map<String, String> unitChars = profileChars(sel, "Unit");
        if (unitChars != null) {
            String name = sel.getAttribute("name");
            int count = intAttr(sel, "number", 1);
            int t  = parseint(unitChars.get("T"), 4);
            int w  = parseint(unitChars.get("W"), 1);
            int sv = threshold(unitChars.get("Sv"), 7);
            return List.of(new ParsedUnit(name, count, t, w, sv, List.copyOf(findWeapons(sel))));
        }
        List<ParsedUnit> units = new ArrayList<>();
        Element inner = child(sel, "selections");
        if (inner != null) {
            for (Element c : children(inner, "selection")) {
                units.addAll(findUnits(c));
            }
        }
        return units;
    }

    // Finds weapon selections within a unit selection (recursive to handle nested upgrades).
    private List<ParsedWeapon> findWeapons(Element sel) {
        List<ParsedWeapon> weapons = new ArrayList<>();
        Element inner = child(sel, "selections");
        if (inner == null) return weapons;
        for (Element c : children(inner, "selection")) {
            Map<String, String> chars = profileChars(c, "Ranged Weapons");
            if (chars == null) chars = profileChars(c, "Melee Weapons");
            if (chars != null) {
                weapons.add(buildWeapon(c, chars));
            } else {
                weapons.addAll(findWeapons(c));
            }
        }
        return weapons;
    }

    private ParsedWeapon buildWeapon(Element sel, Map<String, String> chars) {
        String bs = chars.get("BS") != null ? chars.get("BS") : chars.get("WS");
        return new ParsedWeapon(
            sel.getAttribute("name"),
            intAttr(sel, "number", 1),
            chars.getOrDefault("A", "1"),
            threshold(bs, 4),
            parseint(chars.get("S"), 4),
            parseint(chars.get("AP"), 0),
            chars.getOrDefault("D", "1"),
            parseKeywords(chars.get("Keywords"))
        );
    }

    // Returns the characteristics of the first profile matching typeName, or null if absent.
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
                map.put(c.getAttribute("name"), c.getTextContent().trim());
            }
        }
        return map;
    }

    // --- DOM helpers ---

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

    // --- Value parsers ---

    /** "3+" → 3, "3+/4++" → 3, "N/A" → fallback */
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
            .filter(s -> !s.isEmpty())
            .toList();
    }

    // --- I/O ---

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
            // XXE protection
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
