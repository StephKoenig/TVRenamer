package org.tvrenamer.controller.util;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * Per-thread cache of compiled XPath expressions. XPath compilation is
     * expensive and the same expression strings are used repeatedly when
     * parsing episode data.
     */
    private static final ThreadLocal<Map<String, XPathExpression>> EXPR_CACHE =
        ThreadLocal.withInitial(HashMap::new);

    private static XPathExpression compile(String expression)
        throws XPathExpressionException {
        Map<String, XPathExpression> cache = EXPR_CACHE.get();
        XPathExpression compiled = cache.get(expression);
        if (compiled == null) {
            compiled = XPATH.get().compile(expression);
            cache.put(expression, compiled);
        }
        return compiled;
    }

    public static NodeList nodeListValue(String name, Node eNode)
        throws XPathExpressionException {
        return (NodeList) compile(name).evaluate(eNode, XPathConstants.NODESET);
    }

    public static String nodeTextValue(String name, Node eNode)
        throws XPathExpressionException {
        Node node = (Node) compile(name).evaluate(eNode, XPathConstants.NODE);
        if (node == null) {
            return null;
        }
        return node.getTextContent();
    }
}
