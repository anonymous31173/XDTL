package org.mmx.xdtl.services;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.mmx.xdtl.log.XdtlLogger;
import org.mmx.xdtl.model.XdtlException;

public class PathList {
    private static final Logger logger = XdtlLogger.getLogger("xdtl.rt.pathList");
    private static final String PATH_SEPARATOR = ",";

    private ArrayList<URL> m_roots = new ArrayList<URL>();
    private URL m_baseUrl;

    public PathList(PathList src) {
        m_baseUrl = src.m_baseUrl;
        m_roots.addAll(src.m_roots);
    }

    public PathList(String baseUrl, String paths) {
        if (paths == null || paths.length() == 0) return;

        try {
            m_baseUrl = new URL(baseUrl);
            String[] pathArr = paths.split(PATH_SEPARATOR);
            for (int i = 0; i < pathArr.length; i++) {
                String path = pathArr[i].trim();
                if (!path.endsWith("/")) {
                    path = path + "/";
                }
                m_roots.add(new URL(m_baseUrl, path));
            }
        } catch (MalformedURLException e) {
            throw new XdtlException(e);
        }
    }

    public Object forEachFile(FilenameFilter filter, ForEachCallback callback) {
        for (URL root: m_roots) {
            if (!root.getProtocol().equalsIgnoreCase("file")) continue;

            if (logger.isTraceEnabled()) {
                logger.trace("forEachFile: directory=" + root.getPath());
            }

            File directory = new File(root.getPath());
            if (!directory.exists() || !directory.isDirectory()) continue;
            File[] files = directory.listFiles(filter);
            Arrays.sort(files);

            for (int i = 0; i < files.length; i++) {
                Object result = callback.execute(files[i]);
                if (result != null) return result;
            }
        }

        return null;
    }

    public List<URL> getRoots() {
        return Collections.unmodifiableList(m_roots);
    }

    public void prepend(URL url) {
        m_roots.add(0, getDirectoryUrl(url));
    }

    private URL getDirectoryUrl(URL url) {
        String path = url.getPath();
        if (path.endsWith("/")) return url;

        try {
            return new URL(url, "./");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public interface ForEachCallback {
        Object execute(File file);
    }

    public String toCsv() {
        StringBuilder buf = new StringBuilder();
        for (URL url: m_roots) {
            buf.append(escapeCsv(url)).append(PATH_SEPARATOR);
        }

        if (m_roots.size() > 0) {
            buf.setLength(buf.length() - 1);
        }

        if (logger.isTraceEnabled()) {
            logger.trace("toCsv: result=" + buf);
        }

        return buf.toString();
    }

    private String escapeCsv(URL url) {
        String str = url.toString();
        str = str.replaceAll(PATH_SEPARATOR, "\\" + PATH_SEPARATOR);
        str = str.replaceAll("\\\\", "\\\\\\\\");
        return str;
    }
}
