package org.mmx.xdtl.db.converter;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;

import org.mmx.xdtl.db.Column;

public class StringConverter implements IConverter<String> {
	
    @Override
    public Object convert(String str, Column column) throws SQLException {
        if (str == null) {
            return null;
        }
        
        switch (column.getType()) {
        case Types.CHAR:
        case Types.CLOB:
        case Types.LONGNVARCHAR:
        case Types.LONGVARCHAR:
        case Types.NCHAR:
        case Types.NCLOB:
        case Types.NVARCHAR:
        case Types.VARCHAR:
            return str;
        
        case Types.BIGINT:
        case Types.BOOLEAN:
        case Types.DECIMAL:
        case Types.DOUBLE:
        case Types.FLOAT:
        case Types.INTEGER:
        case Types.NUMERIC:
        case Types.REAL:
        case Types.ROWID:
        case Types.SMALLINT:
        case Types.TINYINT:
            str = str.replace(',', '.');
			return Double.parseDouble(str);
        case Types.DATE:
            return Date.valueOf(str);

        case Types.TIME:
            return Time.valueOf(str);
            
        case Types.TIMESTAMP:
            return Timestamp.valueOf(str);
        }
        
        throw new SQLException("Unsupported data type: " + column.getTypeName());
    }
}
