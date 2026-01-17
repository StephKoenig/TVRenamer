package org.tvrenamer.controller.util;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XPathUtilities {

    private static final XPathFactory XPATH_FACTORY =
        XPathFactory.newInstance();

    /**
     * XPath instances are not guaranteed to be thread-safe. Use a ThreadLocal
     * instance so callers can safely evaluate XPath expressions from background
     * threads (e.g., threaded preload / downloads).
     */
    private static final ThreadLocal<XPath> XPATH = ThreadLocal.withInitial(
        () -> XPATH_FACTORY.newXPath()
    );

    private static XPath getXPath() {
        return XPATH.get();
    }

    public static NodeList nodeListValue(String name, Node eNode)
        throws XPathExpressionException {
        XPathExpression expr = getXPath().compile(name);
        return (NodeList) expr.evaluate(eNode, XPathConstants.NODESET);
    }

    public static String nodeTextValue(String name, Node eNode)
        throws XPathExpressionException {
        XPathExpression expr = getXPath().compile(name);
        Node node = (Node) expr.evaluate(eNode, XPathConstants.NODE);
        if (node == null) {
            return null;
        }
        return node.getTextContent();
    }
}
