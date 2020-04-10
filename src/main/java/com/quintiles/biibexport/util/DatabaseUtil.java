package com.quintiles.biibexport.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Utility class to deal with a HSQLdb database
 */
public class DatabaseUtil {
    private static DatabaseUtil instance;
    private String connectionUrl;
    private String jdbcDriver;
    private String dbUsername;
    private String dbPassword;

    /**
     * Readup the configuration file, and start HSQLdb.
     */
    private DatabaseUtil() {
        String propFileName = "dbconfig.properties";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);

        Properties props = new Properties();
        try {
            props.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.connectionUrl = props.getProperty("server.connectionurl");
        this.jdbcDriver = props.getProperty("server.jdbcdriver");
        this.dbUsername = props.getProperty("server.user");
        this.dbPassword = props.getProperty("server.password");

    }

    /**
     * Get Singletone instance for DatabaseUtil
     *
     * @return DatabaseUtil Singleton instance
     */
    public static DatabaseUtil getInstance() {
        if (instance == null) {
            instance = new DatabaseUtil();
        }
        return instance;
    }

    /**
     * Get a Single HSQLdb connection to be used in the mock service.
     *
     * @return Connection
     * @throws SQLException Errors related to getting connection
     */
    public Connection getConnection() throws SQLException {
        try {
            Class.forName(jdbcDriver);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Cannot Load Driver");
        }
        Connection connection = DriverManager.getConnection
                (connectionUrl, dbUsername, dbPassword);
        return connection;
    }

    /**
     * Close HSQLDb connetion
     *
     * @param con  Connection to be closed.
     * @throws SQLException
     */
    public void close(Connection con) throws SQLException {
        con.close();
    }
}
