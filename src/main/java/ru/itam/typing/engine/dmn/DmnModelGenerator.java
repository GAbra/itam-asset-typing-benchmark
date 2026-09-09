package ru.itam.typing.engine.dmn;

import ru.itam.typing.rules.CanonicalRule;
import ru.itam.typing.rules.RuleSet;

import java.util.*;

public final class DmnModelGenerator {
    public static final String NAMESPACE = "https://demo.local/itam/typing";
    public static final String MODEL_NAME = "ITAMAssetTyping";
    public static final String DECISION_NAME = "MatchedRules";

    public String generate(RuleSet ruleSet) {
        SortedSet<String> features = new TreeSet<>();
        for (CanonicalRule rule : ruleSet.rules()) {
            if (!rule.enabled()) continue;
            features.addAll(rule.required());
            features.addAll(rule.any());
            features.addAll(rule.forbidden());
        }

        StringBuilder xml = new StringBuilder(64 * 1024);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
           .append("<semantic:definitions xmlns=\"https://github.com/kiegroup/kie-dmn\"\n")
           .append(" xmlns:semantic=\"https://www.omg.org/spec/DMN/20240513/MODEL/\"\n")
           .append(" xmlns:feel=\"https://www.omg.org/spec/DMN/20240513/FEEL/\"\n")
           .append(" id=\"definitions_itam_typing\" namespace=\"").append(NAMESPACE).append("\" name=\"").append(MODEL_NAME).append("\">\n");

        xml.append("  <semantic:decision id=\"decision_matched_rules\" name=\"").append(DECISION_NAME).append("\">\n")
           .append("    <semantic:variable name=\"").append(DECISION_NAME).append("\"/>\n");
        for (String feature : features) {
            xml.append("    <semantic:informationRequirement><semantic:requiredInput href=\"#input_")
               .append(xmlId(feature)).append("\"/></semantic:informationRequirement>\n");
        }
        xml.append("    <semantic:decisionTable id=\"typing_table\" hitPolicy=\"COLLECT\" preferredOrientation=\"Rule-as-Row\">\n");

        for (String feature : features) {
            xml.append("      <semantic:input id=\"col_").append(xmlId(feature)).append("\" label=\"").append(escape(feature)).append("\">\n")
               .append("        <semantic:inputExpression typeRef=\"boolean\"><semantic:text>")
               .append(escape(feature)).append("</semantic:text></semantic:inputExpression>\n")
               .append("      </semantic:input>\n");
        }
        xml.append("      <semantic:output id=\"out_match_code\" name=\"matchCode\" typeRef=\"string\"/>\n");

        int row = 0;
        for (CanonicalRule rule : ruleSet.rules()) {
            if (!rule.enabled()) continue;
            List<String> anyAlternatives = rule.any().isEmpty() ? Collections.singletonList(null) : rule.any();
            for (String anyFeature : anyAlternatives) {
                xml.append("      <semantic:rule id=\"rule_").append(row++).append("_").append(xmlId(rule.ruleId())).append("\">\n");
                for (String feature : features) {
                    String test = "-";
                    if (rule.required().contains(feature)) test = "true";
                    if (rule.forbidden().contains(feature)) test = "false";
                    if (Objects.equals(anyFeature, feature)) test = "true";
                    xml.append("        <semantic:inputEntry><semantic:text>").append(test).append("</semantic:text></semantic:inputEntry>\n");
                }
                String code = rule.ruleId() + "|" + rule.targetType() + "|" +
                        (rule.targetSubtype() == null ? "" : rule.targetSubtype()) + "|" + rule.priority();
                xml.append("        <semantic:outputEntry><semantic:text>\"")
                   .append(escapeFeelString(code)).append("\"</semantic:text></semantic:outputEntry>\n")
                   .append("      </semantic:rule>\n");
            }
        }
        xml.append("    </semantic:decisionTable>\n")
           .append("  </semantic:decision>\n");

        for (String feature : features) {
            xml.append("  <semantic:inputData id=\"input_").append(xmlId(feature)).append("\" name=\"").append(escape(feature)).append("\">\n")
               .append("    <semantic:variable name=\"").append(escape(feature)).append("\" typeRef=\"boolean\"/>\n")
               .append("  </semantic:inputData>\n");
        }
        xml.append("</semantic:definitions>\n");
        return xml.toString();
    }

    private static String xmlId(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String escapeFeelString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
