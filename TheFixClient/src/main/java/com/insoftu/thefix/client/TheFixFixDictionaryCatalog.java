package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TheFixFixDictionaryCatalog {
    private final JsonObject snapshot;

    TheFixFixDictionaryCatalog() {
        this.snapshot = buildSnapshot();
    }

    JsonObject snapshot() {
        return snapshot.copy();
    }

    private JsonObject buildSnapshot() {
        JsonArray versions = new JsonArray();
        for (TheFixFixVersion version : TheFixFixVersion.options()) {
            versions.add(loadVersion(version));
        }

        JsonArray messageTypes = new JsonArray();
        for (TheFixMessageType messageType : TheFixMessageType.options()) {
            messageTypes.add(messageType.toJson());
        }

        return new JsonObject()
                .put("versions", versions)
                .put("messageTypes", messageTypes);
    }

    private JsonObject loadVersion(TheFixFixVersion version) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(version.dictionaryResource())) {
            if (inputStream == null) {
                return new JsonObject()
                        .put("code", version.code())
                        .put("label", version.label())
                        .put("tags", new JsonArray())
                        .put("messages", new JsonObject());
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(inputStream);
            document.getDocumentElement().normalize();

            Map<String, JsonObject> fieldsByName = parseFields(document);
            Map<String, Element> componentsByName = parseComponents(document);
            JsonObject messages = parseMessages(document, fieldsByName, componentsByName);

            List<JsonObject> tags = new ArrayList<>(fieldsByName.values());
            tags.sort(Comparator.comparingInt(field -> field.getInteger("tag", Integer.MAX_VALUE)));

            JsonArray tagArray = new JsonArray();
            tags.forEach(tagArray::add);

            return new JsonObject()
                    .put("code", version.code())
                    .put("label", version.label())
                    .put("beginString", version.beginString())
                    .put("defaultApplVerId", version.defaultApplVerId())
                    .put("tags", tagArray)
                    .put("messages", messages);
        } catch (Exception exception) {
            return new JsonObject()
                    .put("code", version.code())
                    .put("label", version.label())
                    .put("error", exception.getMessage())
                    .put("tags", new JsonArray())
                    .put("messages", new JsonObject());
        }
    }

    private static Map<String, JsonObject> parseFields(Document document) {
        Map<String, JsonObject> fieldsByName = new LinkedHashMap<>();
        Element fieldsRoot = firstElement(document.getDocumentElement(), "fields");
        if (fieldsRoot == null) {
            return fieldsByName;
        }

        NodeList nodes = fieldsRoot.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!(node instanceof Element field) || !"field".equals(field.getTagName())) {
                continue;
            }

            JsonArray enumValues = new JsonArray();
            NodeList valueNodes = field.getChildNodes();
            for (int j = 0; j < valueNodes.getLength(); j++) {
                Node valueNode = valueNodes.item(j);
                if (valueNode instanceof Element valueElement && "value".equals(valueElement.getTagName())) {
                    enumValues.add(new JsonObject()
                            .put("code", valueElement.getAttribute("enum"))
                            .put("label", valueElement.getAttribute("description")));
                }
            }

            fieldsByName.put(field.getAttribute("name"), new JsonObject()
                    .put("tag", parseInt(field.getAttribute("number")))
                    .put("name", field.getAttribute("name"))
                    .put("type", field.getAttribute("type"))
                    .put("enumValues", enumValues));
        }
        return fieldsByName;
    }

    private static Map<String, Element> parseComponents(Document document) {
        Map<String, Element> componentsByName = new HashMap<>();
        Element componentsRoot = firstElement(document.getDocumentElement(), "components");
        if (componentsRoot == null) {
            return componentsByName;
        }

        NodeList nodes = componentsRoot.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element component && "component".equals(component.getTagName())) {
                componentsByName.put(component.getAttribute("name"), component);
            }
        }
        return componentsByName;
    }

    private static JsonObject parseMessages(Document document,
                                            Map<String, JsonObject> fieldsByName,
                                            Map<String, Element> componentsByName) {
        JsonObject messages = new JsonObject();
        Element messagesRoot = firstElement(document.getDocumentElement(), "messages");
        if (messagesRoot == null) {
            return messages;
        }

        Map<TheFixMessageType, String> dictionaryNames = Map.of(
                TheFixMessageType.NEW_ORDER_SINGLE, "NewOrderSingle",
                TheFixMessageType.ORDER_CANCEL_REPLACE_REQUEST, "OrderCancelReplaceRequest",
                TheFixMessageType.ORDER_CANCEL_REQUEST, "OrderCancelRequest"
        );

        Map<String, Element> messagesByName = new HashMap<>();
        NodeList nodes = messagesRoot.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element message && "message".equals(message.getTagName())) {
                messagesByName.put(message.getAttribute("name"), message);
            }
        }

        for (TheFixMessageType messageType : TheFixMessageType.options()) {
            Element message = messagesByName.get(dictionaryNames.get(messageType));
            if (message == null) {
                messages.put(messageType.code(), new JsonArray());
                continue;
            }

            Set<String> fieldNames = new LinkedHashSet<>();
            collectFieldNames(message, componentsByName, fieldNames);
            List<JsonObject> fields = new ArrayList<>();
            for (String fieldName : fieldNames) {
                JsonObject field = fieldsByName.get(fieldName);
                if (field != null) {
                    fields.add(field.copy());
                }
            }
            fields.sort(Comparator.comparingInt(item -> item.getInteger("tag", Integer.MAX_VALUE)));

            JsonArray fieldArray = new JsonArray();
            fields.forEach(fieldArray::add);
            messages.put(messageType.code(), fieldArray);
        }

        return messages;
    }

    private static void collectFieldNames(Element parent, Map<String, Element> componentsByName, Set<String> fieldNames) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }

            switch (element.getTagName()) {
                case "field" -> fieldNames.add(element.getAttribute("name"));
                case "component" -> {
                    Element component = componentsByName.get(element.getAttribute("name"));
                    if (component != null) {
                        collectFieldNames(component, componentsByName, fieldNames);
                    }
                }
                case "group" -> {
                    fieldNames.add(element.getAttribute("name"));
                    collectFieldNames(element, componentsByName, fieldNames);
                }
                default -> {
                }
            }
        }
    }

    private static Element firstElement(Element parent, String name) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static int parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
