package org.mmx.xdtl.runtime.command;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Statement;

import org.mmx.xdtl.db.CsvSource;
import org.mmx.xdtl.db.DbfSource;
import org.mmx.xdtl.db.ExcelSource;
import org.mmx.xdtl.db.JdbcConnection;
import org.mmx.xdtl.db.Loader;
import org.mmx.xdtl.db.Source;
import org.mmx.xdtl.model.Connection;
import org.mmx.xdtl.model.XdtlException;
import org.mmx.xdtl.runtime.Context;
import org.mmx.xdtl.runtime.RuntimeCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class JdbcReadCmd implements RuntimeCommand {
    private final Logger m_logger = LoggerFactory.getLogger(JdbcReadCmd.class);
    
    private final String m_source;
    private final String m_target;
    private final SourceType m_sourceType;
    private final String m_delimiter;
    private final String m_quote;
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
        DBF;
        
        public static SourceType valueOfIgnoreCase(String name) {
            for (SourceType type: values()) {
                if (type.name().equalsIgnoreCase(name)) return type;
            }
            return null;
        }
    };
    
    public JdbcReadCmd(String source, String target, String type,
            boolean overwrite, String delimiter, String quote, String encoding,
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
        m_commitRowCount = commitRowCount;

        m_sourceType = SourceType.valueOfIgnoreCase(type);
        if (m_sourceType == null) {
            throw new XdtlException("Invalid type: '" + type + "', must be one of " + SourceType.values());
        }
    }

    @Override
	public void run(Context context) throws Throwable {
        m_logger.info(String.format(
                "read: source='%s', target='%s', " +
                "type='%s', delimiter='%s', quote='%s', encoding='%s', " +
                "connection='%s', errors='%s', header=%s, skip=%d, batch=%d",
                m_source, m_target,
                m_sourceType, m_delimiter, m_quote, m_encoding, m_connection,
                m_errors, m_header, m_skip, m_commitRowCount));

        JdbcConnection cnn = context.getConnectionManager().getJdbcConnection(m_connection);
        if (m_overwrite)
        	truncateTarget(cnn);
        
        org.mmx.xdtl.db.Source source = null;
        
        switch (m_sourceType) {
        case CSV:
            FileInputStream stream = new FileInputStream(m_source);
            source = new CsvSource(stream, m_encoding, m_delimiter.charAt(0), m_quote.charAt(0), m_header, m_skip);
            break;
        case EXCEL:
            String[] arr = m_source.split("#");
            String sheetName = arr.length > 1 ? arr[1] : null;
            stream = new FileInputStream(arr[0]);
            source = new ExcelSource(stream, sheetName, m_header, m_skip);
            break;
        case DBF:
            source = new DbfSource(m_source, m_encoding, m_skip);
        }

        try {
            loadTarget(cnn, source);
        } finally {
            try {
                source.close();
            } catch (IOException e) {
                m_logger.warn("Failed to close source", e);
            }
        }
	}

	private void loadTarget(JdbcConnection cnn, Source source) throws Exception {
        Loader loader = new Loader(cnn, m_target, m_batchSize, m_commitRowCount);
        int rowCount = 0;
        
        try {
            Object[] values;
            
            while ((values = source.readNext()) != null) {
                handleNulls(values);
                loader.loadRow(values);
                rowCount++;
            }
        } finally {
            loader.close();
        }
        
        m_logger.info(String.format("%d row(s) loaded", rowCount));
    }

    private void handleNulls(Object[] values) {
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

    private void truncateTarget(JdbcConnection cnn) throws Exception {
        Statement stmt = cnn.createStatement();
        try {
            m_logger.info("Truncating table '{}'", m_target);
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
            m_logger.warn("Failed to close statement", t);
        }
	}
    
    @Inject
    protected void setLoaderBatchSize(@Named("loader.batchsize") int batchSize) {
        m_batchSize = batchSize;
    }
}