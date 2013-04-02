package org.rhq.core.db.setup;

import java.util.List;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import org.rhq.core.db.MySqlDatabaseType;
import org.rhq.core.db.builders.CreateSequenceExprBuilder;

/**
 * This class is used to handle the "sequence" which defined in db_schema_combined.xml.
 * @author huchangchun
 *
 */
class MySqlColumn extends Column {

    protected MySqlColumn(Node node, Table table) throws SAXException {
        super(node, table);
    }

    //Do nothing for default in this mothod. As the MySQL need handle the auto_increment after table created.
    @SuppressWarnings("rawtypes")
    protected String getDefaultCommand(List cmds) {
        if (m_strType.equalsIgnoreCase("auto") || (m_iDefault == Column.DEFAULT_SEQUENCE_ONLY)) {
            return " AUTO_INCREMENT";
        } else {
            return super.getDefaultCommand(cmds);
        }
    }

    /**
     * NOT NULL clause should be before DEFAULT or AUTO_INCREMENT. Refer to http://dev.mysql.com/doc/refman/5.6/en/create-table.html.
     */
    protected String compositeDefaultAndRequired(List cmds, String strCmd) {
        if (this.isRequired()) {
            strCmd += " NOT NULL";
        }

        if (this.hasDefault()) {
            String strDefault = this.getDefaultCommand(cmds);

            if (strDefault.length() > 0) {
                strCmd = strCmd + ' ' + strDefault;
            }
        }

        if (this.m_sDefault != null) {
            if (this.m_strType.equalsIgnoreCase("boolean")) {
                strCmd += " DEFAULT " + this.getsDefault() + "";
            } else {
                strCmd += " DEFAULT '" + this.getsDefault() + "'";
            }
        }
        return strCmd;
    }

    /**
     * Do nothing here, generally, this method is used to create the sequence in Postgresql, Oracle, or H2 database. However, MySQL doesn't support sequence. 
     * Instead, it use auto increment to mock the function. In this way, we will alter the append the SQL after the table is created. So we put the logic in <method>MySqlColumn.getPostCreateCommands()</method> 
     */
    @SuppressWarnings("rawtypes")
    protected void getPreCreateCommands(List cmds) {
        return;
    }

    /**
     * 1. alter table set auto_increment if column is auto_increment.
     * 2. alter table set row_format=dynamic if column size is more than 256 bytes. With corresponding configuration in MySQL, it increases the max key length from 767 to 3072.
     */
    protected void getPostCreateCommands(List cmds) {
        if (hasDefault()) {
            switch (getDefault()) {
            case Column.DEFAULT_SEQUENCE_ONLY:
            case Column.DEFAULT_AUTO_INCREMENT:
                cmds.add(buildSequenceSqlExpr(CreateSequenceExprBuilder.getBuilder(MySqlDatabaseType.VENDOR_NAME),
                    getSequenceName()));
                break;
            }
        }

        if (m_iSize >= 256) {
            cmds.add("ALTER TABLE " + this.m_strTableName + " ROW_FORMAT=DYNAMIC");
        }
    }

}
