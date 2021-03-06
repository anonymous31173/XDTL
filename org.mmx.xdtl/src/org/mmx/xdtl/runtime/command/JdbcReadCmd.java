package org.mmx.xdtl.runtime.command;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Statement;
import java.util.List;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mmx.xdtl.db.CsvSource;
import org.mmx.xdtl.db.DbfSource;
import org.mmx.xdtl.db.ExcelSource;
import org.mmx.xdtl.db.JdbcConnection;
import org.mmx.xdtl.db.Loader;
import org.mmx.xdtl.db.RowHandler;
import org.mmx.xdtl.db.RowSet;
import org.mmx.xdtl.db.RowSetSource;
import org.mmx.xdtl.db.Source;
import org.mmx.xdtl.db.XmlSource;
import org.mmx.xdtl.log.XdtlLogger;
import org.mmx.xdtl.model.Connection;
import org.mmx.xdtl.model.XdtlException;
import org.mmx.xdtl.runtime.Context;
import org.mmx.xdtl.runtime.RuntimeCommand;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class JdbcReadCmd implements RuntimeCommand {
    private static final Logger logger = XdtlLogger.getLogger("xdtl.cmd.read");

    private final Object m_source;
    private final String m_target;
    private final SourceType m_sourceType;
    private final String m_delimiter;
    private final String m_quote;
    private final String m_escape;
    private final String m_errors;
    private final boolean m_overwrite;
    private final String m_encoding;
    private final Connection m_connection;
    private final boolean m_header;
    private final int m_skip;
    private final int m_commitRowCount;
    private int m_batchSize;

    private enum SourceType {
        CSV,
        EXCEL,
        DBF,
        ROWSET,
        XML;

        public static SourceType valueOfIgnoreCase(String name) {
            for (SourceType type: values()) {
                if (type.name().equalsIgnoreCase(name)) return type;
            }
            return null;
        }
    }

    public JdbcReadCmd(Object source, String target, String type,
            boolean overwrite, String delimiter, String quote, String encoding, String escape,
            Connection cnn, String errors, boolean header, int skip, int commitRowCount) {

        m_source = source;
        m_target = target;
        m_delimiter = delimiter;
        m_quote = quote;
        m_encoding = encoding;
        m_overwrite = overwrite;
        m_connection = cnn;
        m_errors = errors;
        m_header = header;
        m_skip = skip;
        m_escape = escape;
        m_commitRowCount = commitRowCount;

        m_sourceType = SourceType.valueOfIgnoreCase(type);
        if (m_sourceType == null) {
            throw new XdtlException("Invalid type: '" + type + "', must be one of " + SourceType.values());
        }
    }

    @Override
	public void run(Context context) throws Throwable {
        logCmdStart();
        JdbcConnection cnn = context.getConnectionManager().getJdbcConnection(m_connection);
        if (m_overwrite)
        	truncateTarget(cnn);

        org.mmx.xdtl.db.Source source = null;

        switch (m_sourceType) {
        case CSV: {
                URL url = new URL(new URL("file:"), (String) m_source);
                InputStream stream = url.openStream();
                source = new CsvSource(stream, m_encoding, m_delimiter.charAt(0), m_quote.charAt(0), m_header, m_skip,
                        StringEscapeUtils.unescapeJava(m_escape).charAt(0));
            }
            break;
        case EXCEL: {
                String[] arr = ((String)m_source).split("#");
                String sheetName = arr.length > 1 ? arr[1] : null;
                URL url = new URL(new URL("file:"), (String) arr[0]);
                InputStream stream = url.openStream();
                source = new ExcelSource(stream, sheetName, m_header, m_skip);
            }
            break;
        case DBF:
            source = new DbfSource((String) m_source, m_encoding, m_skip);
            break;
        case ROWSET:
            source = new RowSetSource((RowSet)m_source, m_skip);
            break;
        case XML:
            source = new XmlSource((String) m_source);
            break;
        }

        try {
            loadTarget(cnn, source);
        } finally {
            try {
                source.close();
            } catch (IOException e) {
                logger.warn("Failed to close source", e);
            }
        }
	}

	private void logCmdStart() {
        if (logger.isDebugEnabled()) {
            if (m_sourceType.equals(SourceType.ROWSET)) {
                logger.debug(String.format("rowsetType=%s, target='%s'",
                        m_source.getClass().getName(), m_target));
            } else {
                logger.debug(String.format("source=%s, type=%s, target=%s," +
                		" delimiter=%s, quote=%s, errors=%s, overwrite=%s," +
                		" encoding=%s, header=%s, skip=%d, batch=%d",
                		m_source, m_sourceType, m_target, m_delimiter, m_quote,
                		m_errors, m_overwrite, m_encoding, m_header, m_skip,
                		m_commitRowCount));
            }
        } else {
            logger.info("target=" + m_target);
        }
    }

    private void loadTarget(JdbcConnection cnn, Source source) throws Exception {
        final Loader loader = new Loader(cnn, m_target, m_batchSize, m_commitRowCount);

        try {
            source.fetchRows(new RowHandler() {
                @Override
                public void handleRow(Object[] data, List<String> columnNames) throws Exception {
                    replaceEmptyStringsWithNulls(data);

                    if (logger.isTraceEnabled()) {
                        logger.trace("handleRow: data=" + data + ", columnNames=" + columnNames);
                    }
                    loader.loadRow(data, columnNames);
                }

                private void replaceEmptyStringsWithNulls(Object[] values) {
                    for (int i = 0; i < values.length; i++) {
                        Object obj = values[i];
                        if (obj instanceof String) {
                            String str = (String) obj;
                            if (str != null && str.length() == 0) {
                                values[i] = null;
                            }
                        }
                    }
                }

            });
        } finally {
            loader.close();
        }

        logger.log( logger.isDebugEnabled() ? Level.DEBUG : Level.INFO,
                String.format("%d row(s) loaded", loader.getRowCount()));
    }

    private void truncateTarget(JdbcConnection cnn) throws Exception {
        Statement stmt = cnn.createStatement();
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("Truncating table " + m_target);
            }
            stmt.execute(getTruncateSql(m_target));
        } finally {
            close(stmt);
        }
	}

	protected String getTruncateSql(String target) {
	    return "delete from " + target;
    }

    private void close(Statement stmt) {
        try {
            stmt.close();
        } catch (Throwable t) {
            logger.warn("Failed to close statement", t);
        }
	}

    @Inject
    protected void setLoaderBatchSize(@Named("loader.batchsize") int batchSize) {
        m_batchSize = batchSize;
    }
}
