package io.github.mavenmcp.parser;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Shared XML parsing utilities with secure defaults.
 */
public final class XmlUtils {

    private XmlUtils() {
    }

    /**
     * Create a {@link DocumentBuilder} with external entities disabled.
     *
     * @return secure document builder
     * @throws ParserConfigurationException if the parser cannot be configured
     */
    public static DocumentBuilder newSecureDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder();
    }
}
