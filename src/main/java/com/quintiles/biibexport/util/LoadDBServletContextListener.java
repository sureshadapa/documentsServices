package com.quintiles.biibexport.util;

import org.hsqldb.Server;
import org.hsqldb.persist.HsqlProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Database Context Listener to Bring up and Shutdown the Hypersonic Database.
 *
 * Reads the database configuration properties and inits/destroys itself during
 * context create and destroy.
 *
 */
public class LoadDBServletContextListener implements ServletContextListener {
    private static Logger logger = LoggerFactory.getLogger(LoadDBServletContextListener.class);

    private final static String CONFIGURATION_ELEMENT = "Configuration";

    private ServletContext context;
    private Server hsqlServer = null;
    private ExecutorService executor;

    public void contextInitialized(ServletContextEvent contextEvent) {
        context = contextEvent.getServletContext();
        try{
            String propFileName = "/WEB-INF/classes/dbconfig.properties";
            InputStream inputStream = this.context.getResourceAsStream(propFileName);

            Properties props = new Properties();
            props.load(inputStream);
            if (inputStream == null) {
                throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
            }

            HsqlProperties hprops = new HsqlProperties();
            hprops.setProperty("server.database.0",props.getProperty("server.database"));
            hprops.setProperty("server.dbname.0",props.getProperty("server.dbname"));

            hsqlServer = new Server();
            hsqlServer.setProperties(hprops);
            hsqlServer.setLogWriter(null);
            hsqlServer.setSilent(true);

            // Start the engine!
            hsqlServer.start();

            Connection connection = null;
            try{
                connection = DatabaseUtil.getInstance().getConnection();
                connection.prepareStatement
                        ("drop table Sessions if exists;").execute();
                connection.prepareStatement
                        ("create table Sessions (Id integer IDENTITY, Created_Date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                                "TokenValue varchar(50),IP_Address varchar(15),Status char(1));")
                        .execute();

                connection.prepareStatement("drop table Failed_Attempts if exists;").execute();
                connection.prepareStatement("create table Failed_Attempts (Id integer IDENTITY," +
                        "IP_Address varchar(15),Fail_Count integer, Created_Date TIMESTAMP DEFAULT CURRENT_TIMESTAMP);").
                        execute();
                //connection.prepareStatement("drop SEQUENCE Phlex_Documentid if exists;").execute();
                connection.prepareStatement("create SEQUENCE IF NOT EXISTS Phlex_Documentid as BIGINT;").execute();
            } catch (SQLException e) {
                logger.error("Error Running Query: " + e.getMessage(), e);
            } finally {
                DatabaseUtil.getInstance().close(connection);
            }

            // Grab Configuration and Store in ServletContext
            getHarnessConfiguration();

            // Start Executor service now
            executor = Executors.newSingleThreadExecutor();
            executor.submit(new KillSessionTask());

        }catch(Exception e){
            logger.error("Error Initing DB servlet: " + e.getMessage(), e);
        }
        context.setAttribute("hsqlServer", hsqlServer);
    }

    public void contextDestroyed(ServletContextEvent contextEvent) {
        executor.shutdown();     // Kill the executor

        Connection connection = null;
        try{
            connection = DatabaseUtil.getInstance().getConnection();
            Statement st = connection.createStatement();
            st.execute("SHUTDOWN");
        } catch(SQLException sqlEx) {
            logger.error("Error Running Shutdown", sqlEx);
        }
        if(hsqlServer != null){
            hsqlServer.stop();
            hsqlServer.shutdown();
        }
        this.context = null;
    }

    private void getHarnessConfiguration() throws Exception {
        try {
            DocumentBuilderFactory builderFactory =  DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document document = builder.parse(new FileInputStream(Constants.TESTHARNESS_CONFIGFILE));
            Element rootElement = document.getDocumentElement();
            NodeList confignode = rootElement.getElementsByTagName(CONFIGURATION_ELEMENT);
            Element elc = (Element)confignode.item(0);
            String sessionTimeout =  getTextValue(elc, Constants.SESSION_TIMEOUT);
            String maxInvalidAttempts = getTextValue(elc, Constants.MAX_INVALID_ATTEMPTS);
            String timeBetweenRetries = getTextValue(elc, Constants.TIME_BETWEEN_RETRIES);
            String sleepTime = getTextValue(elc, Constants.SLEEP_TIME);
            String concurrentConnections = getTextValue(elc, Constants.CONCURRENT_CONNECTIONS);

            // Store them in context.
            context.setAttribute(Constants.SESSION_TIMEOUT, sessionTimeout);
            context.setAttribute(Constants.MAX_INVALID_ATTEMPTS, maxInvalidAttempts);
            context.setAttribute(Constants.TIME_BETWEEN_RETRIES, timeBetweenRetries);
            context.setAttribute(Constants.SLEEP_TIME, sleepTime);
            context.setAttribute(Constants.CONCURRENT_CONNECTIONS, concurrentConnections);
        } catch(ParserConfigurationException ce) {
            logger.error("Parser Configuration Error: " + ce.getMessage(), ce);
            throw ce;
        } catch(Exception ex) {
            logger.error("Error Getting Session timeout: " + ex.getMessage(), ex);
        }
    }


    private String getTextValue(Element ele, String tagName) {
        String textVal = null;
        NodeList nl = ele.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            Element el = (Element)nl.item(0);
            textVal = el.getFirstChild().getNodeValue();
        }
        return textVal;
    }

    private class KillSessionTask implements Runnable {

        public void run() {
            while (true) {
                String timeout =(String) context.getAttribute(Constants.SESSION_TIMEOUT);

                if (timeout == null) {
                    return;
                }

                Connection connection = null;
                logger.trace("Attempting cleanup now...");
                String sql = String.format("delete From Sessions where DATEDIFF('second', DATEADD('second', %s, Created_Date) , current_timestamp) > 0", timeout);
                try {
                    connection = DatabaseUtil.getInstance().getConnection();
                    int count = connection.prepareStatement(sql).executeUpdate();
                    if (count > 0) {
                        logger.info(String.format("Deleted %d records due to session inactivity of %s seconds", count, timeout));
                    } else {
                        logger.trace("Nothing to cleanup");
                    }
                } catch(SQLException sqlEx) {
                    logger.error("Error executing SQL: " + sqlEx.getMessage(), sqlEx);
                } finally {
                    try {
                        connection.close();
                    } catch(SQLException ex) {}
                }   
                        
                try {
                    Thread.sleep(10000);
                } catch(InterruptedException ie) {

                }
            }
        }
    }
}
