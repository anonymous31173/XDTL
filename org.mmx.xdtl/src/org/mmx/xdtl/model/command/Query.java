package org.mmx.xdtl.model.command;

import org.mmx.xdtl.model.AbstractElement;
import org.mmx.xdtl.model.Command;

public class Query extends AbstractElement implements Command {
    public static final String QUERYTYPE_SQL = "sql";

    private final String m_source;
    private final String m_connection;
    private final String m_queryType;
    private final String m_target;

    public Query(String source, String connection, String queryType, String target) {
        super();
        m_source = source;
        m_connection = connection;
        m_queryType = queryType;
        m_target = target;
    }

    public String getSource() {
        return m_source;
    }

    public String getConnection() {
        return m_connection;
    }

    public String getQueryType() {
        return m_queryType;
    }

    public String getTarget() {
        return m_target;
    }
}
