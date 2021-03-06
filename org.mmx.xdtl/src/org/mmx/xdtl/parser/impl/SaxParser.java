package org.mmx.xdtl.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.SAXParser;

import org.apache.log4j.Logger;
import org.mmx.xdtl.debugger.Debugger;
import org.mmx.xdtl.log.XdtlLogger;
import org.mmx.xdtl.model.Package;
import org.mmx.xdtl.model.SourceLocator;
import org.mmx.xdtl.model.XdtlException;
import org.mmx.xdtl.parser.Parser;
import org.xml.sax.SAXParseException;

import com.google.inject.Inject;
import com.google.inject.internal.Nullable;

/**
 * XDTL parser implementation using SAX. Same instance of the parser can be used
 * repeatedly, it will retain all schemas used in the process.
 *
 * @author vsi
 */
public class SaxParser implements Parser {
    private static final Logger logger = XdtlLogger.getLogger("xdtl.rt.parser.saxParser");
    private final SAXParser m_saxParser;
    private final ElementHandlerSet m_elementHandlerSet;
    private final Debugger m_debugger;

    @Inject
    SaxParser(SAXParser saxParser, ElementHandlerSet elementHandlerSet, @Nullable Debugger debugger) {
        m_saxParser = saxParser;
        m_elementHandlerSet = elementHandlerSet;
        m_debugger = debugger;
    }

    @Override
    public Package parse(URLConnection cnn) {
        URL url = cnn.getURL();
        if (url.getRef() != null) {
            throw new XdtlException("Reference is not allowed in package url: url='" + url + "'");
        }

        try {
            Package pkg;
            Handler handler = new Handler(url.toString(), m_elementHandlerSet, m_debugger);
            InputStream is = cnn.getInputStream();

            try {
                logger.trace("Parsing '" + url + "'");
                m_saxParser.parse(is, handler);
            } finally {
                closeInputStream(is);
            }

            pkg = handler.getPackage();
            pkg.setUrl(url);
            logger.trace("Parsed package '" + pkg.getName() + "' from '" + url + "'");
            return pkg;
        } catch (SAXParseException e) {
            SourceLocator locator = new SourceLocator(url.toString(), e.getLineNumber(), "");
            throw new XdtlException(locator, e);
        } catch (Exception e) {
            throw new XdtlException(e);
        }
    }

    private void closeInputStream(InputStream is) {
        try {
            is.close();
        } catch (IOException e) {
            logger.warn("Failed to close input stream", e);
        }
    }
}
