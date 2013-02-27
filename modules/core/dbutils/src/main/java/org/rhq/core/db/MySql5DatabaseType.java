package org.rhq.core.db;

public class MySql5DatabaseType extends MySqlDatabaseType {

    @Override
    public String getName() {
        return MySqlDatabaseType.VENDOR_NAME + getVersion();
    }

    @Override
    public String getVersion() {
        return "5";
    }

    
 // TODO NEED Check, use parent's org.hibernate.dialect.MySQLDialect but not MySQL5InnoDBDialect in ConfigurationBean. It is not consistent. Correct it later.
    /**
    public String getHibernateDialect() {
         whether it is correct or not. It is copied from ConfirgurationBean MySQL5InnoDBDialect.
        return "org.hibernate.dialect.MySQLDialect";
        // TODO NEED check, I removed getHibernateDialect() method from MySQLDatabaseType class and kept its return result here from information.
        // return "org.hibernate.dialect.MySQLDialect";
    }
    */

    public String getHibernateDialect() {
        return "org.hibernate.dialect.MySQL5Dialect";
    }
}
