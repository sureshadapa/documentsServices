package com.quintiles.biibexport.harness;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import com.quintiles.biibexport.util.*;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.io.*;
import java.sql.*;
import java.util.*;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.WebServiceContext;

import org.slf4j.MDC;
import org.xml.sax.SAXException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Mock Implementation of the Phlex Webservice available.
 *
 * Implements Login, Logout, SubmitDocument and SubmitDocumentMetadata
 *
 */
@org.apache.cxf.interceptor.InInterceptors (interceptors = {"com.quintiles.biibexport.util.InCounterInterceptor"})
@org.apache.cxf.interceptor.OutInterceptors (interceptors = {"com.quintiles.biibexport.util.OutCounterInterceptor"})
public class PhlexViewServiceImpl extends IExternalConnectorImpl {

    @Resource
    WebServiceContext context;

    private static Logger LOG = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(PhlexViewServiceImpl.class);
    private final static String USER_CONFIGFILE = System.getProperty("catalina.base") + File.separator + "conf"+ File.separator +"users.cfg";

    private final static QName Token_QNAME = new QName("http://www.phlexeview.com/", "token");
    private final static QName ErrorData_QNAME = new QName("http://www.phlexeview.com/", "errorData");

    private final String INSERT_SESSIONS = "insert into Sessions(TokenValue,IP_Address,Status) values(?,?,?)";
    private final String INSERT_FAILED_ATTEMPTS = "insert into Failed_Attempts(IP_Address,Fail_Count) values(?,?)";
    private final String SELECT_FAILED_ATTEMPTS = "select count(Fail_Count) from Failed_Attempts where IP_Address = ?";
    private final String DELETE_FAILED_ATTEMPTS = "delete from Failed_Attempts where IP_Address = ?";
    private final String DELETE_SESSIONS = "delete from Sessions where TokenValue=?";
    private final String SELECT_SEQ = "call NEXT VALUE FOR Phlex_Documentid";
    private final String RETRIES_WITHIN_LIMIT = "select 1 From Failed_Attempts " +
            "where DATEDIFF('second', DATEADD('second', %s, Created_Date) , current_timestamp) > 0";

    /*
     * LoginUser Implementation.
     *
     * Checks for username, password and ipaddress (optional) from users.cfg and validates the session. Returns a
     * md5 token as the password.
     *
     * @param userName Username to login to Phlex Webservice
     * @param password Password to login to Phlex Webservice
     * @param ipAddress IPaddress from which this call is made
     * @return SecurityResult SecurityResult  indicating the status of the login call.
     *
     */
    public SecurityResult loginUser(String userName,
                                    String password,
                                    String ipAddress) {

        Object obj = handleExceedConcurrentConnections("loginUser");
        if (obj != null) {
            return (SecurityResult) obj;
        }

        // Move this entire block into some thread and add WAIT response codes as well.
        SecurityResult securityResult = new SecurityResult();
        ServletContext srvContext = (ServletContext) context.getMessageContext().get("HTTP.CONTEXT");
        try{

            DatabaseUtil dbutil = DatabaseUtil.getInstance();
            Connection connection = dbutil.getConnection();

            HierarchicalINIConfiguration userConfObj =  new HierarchicalINIConfiguration(USER_CONFIGFILE);
            SubnodeConfiguration uObj = userConfObj.getSection(userName);
            String maxAttempts = (String) srvContext.getAttribute(Constants.MAX_INVALID_ATTEMPTS);
            if (maxAttempts == null) {
                throw new Exception("Cannot Fetch " + Constants.MAX_INVALID_ATTEMPTS + ", Please check HarnessConfig");
            }

            int maxAttemptsAllowed = Integer.parseInt(maxAttempts);
            LOG.info("maxInvalidAttempts Configured = "+maxAttemptsAllowed);

            String timeBetweenRetries = (String) srvContext.getAttribute(Constants.TIME_BETWEEN_RETRIES);
            if (timeBetweenRetries == null) {
                throw new Exception("Cannot Fetch " + Constants.TIME_BETWEEN_RETRIES+ ", Please check HarnessConfig");
            }

            int betweenRetries = Integer.parseInt(timeBetweenRetries);
            LOG.info("timeBetweenRetries Configured = "+betweenRetries);

            if (uObj != null && uObj.getString("password") != null) {

                if(password.equals(uObj.getString("password"))) {
                    String token = new GenerateTokenMD5(userName).getToken();
                    LOG.info("Token generated is" + token);
                    PreparedStatement insertStatement = connection.prepareStatement(INSERT_SESSIONS);
                    insertStatement.setString(1, token);
                    insertStatement.setString(2, ipAddress);
                    insertStatement.setString(3, "1");
                    int insertResult = insertStatement.executeUpdate();
                    if(insertResult == 1){
                        PreparedStatement deleteStatement = connection.prepareStatement(DELETE_FAILED_ATTEMPTS);
                        deleteStatement.setString(1, ipAddress);
                        int deleteResult = deleteStatement.executeUpdate();
                        LOG.info("DELETE FAILED_ATTEMPTS after successful login.");
                    }
                    securityResult.setToken(new JAXBElement<String>(this.Token_QNAME,
                            String.class, SecurityResult.class, token));
                    securityResult.setStatusCode(StatusCode.SUCCESS);
                    securityResult.setErrorReason(ErrorReason.NONE);
                    LOG.info("SUCCESS. inserted row into Sessions = " +  insertResult);
                } else {
                    PreparedStatement selectAttemptsStatement = connection.prepareStatement(SELECT_FAILED_ATTEMPTS);
                    selectAttemptsStatement.setString(1,ipAddress);
                    ResultSet rs = selectAttemptsStatement.executeQuery();
                    int dbFailCount = 0;
                    while(rs.next()) {
                        dbFailCount = rs.getInt(1);
                        LOG.info("Attempts made for " + ipAddress + " = " + dbFailCount );
                    }
                    rs.close();

                    if (dbFailCount < maxAttemptsAllowed) {
                        PreparedStatement failedAttemptsStatement = connection.prepareStatement(INSERT_FAILED_ATTEMPTS);
                        failedAttemptsStatement.setString(1, ipAddress);
                        failedAttemptsStatement.setInt(2, 1);
                        int failedAttemptsResult = failedAttemptsStatement.executeUpdate();
                        securityResult.setToken(new JAXBElement<String>(this.Token_QNAME,
                                String.class, SecurityResult.class, null));
                        securityResult.setStatusCode(StatusCode.CRITICAL);
                        securityResult.setErrorReason(ErrorReason.USER_PASS_MISMATCH);
                        securityResult.setErrorData(new JAXBElement<String>(this.ErrorData_QNAME,
                                String.class, SecurityResult.class,
                                "Authentication failed because of invalid user/password"));
                        LOG.info("Authentication failed because of invalid user/password: " + failedAttemptsResult);
                    } else {
                        // Check if the next attempt is within retry limit.
                        String sql = String.format(RETRIES_WITHIN_LIMIT, Integer.parseInt(timeBetweenRetries));
                        Statement retryLimitStatement = connection.createStatement();
                        ResultSet retryResultSet = retryLimitStatement.executeQuery(sql);
                        if (retryResultSet.next()) {
                            /* There are records that are greater than retry count. Delete all records in Failed_Attempts
                               and reset the counter by creating a new failure record.
                               Situation: There were three invalid attempts and the fourth attempt was made after the
                               configured retry limit., If so, delete the three invalid attempts and create a fresh
                               record with current time indicating the start of another series.
                             */
                            PreparedStatement deleteStatement = connection.prepareStatement(DELETE_FAILED_ATTEMPTS);
                            deleteStatement.setString(1, ipAddress);
                            int deleteResult = deleteStatement.executeUpdate();
                            LOG.info("DELETE FAILED_ATTEMPTS as all records are past retry attempts.");

                            // Insert as the first failed attempt
                            PreparedStatement failedAttemptsStatement = connection.prepareStatement(INSERT_FAILED_ATTEMPTS);
                            failedAttemptsStatement.setString(1, ipAddress);
                            failedAttemptsStatement.setInt(2, 1);
                            int failedAttemptsResult = failedAttemptsStatement.executeUpdate();

                            securityResult.setToken(new JAXBElement<String>(this.Token_QNAME,
                                    String.class, SecurityResult.class, null));
                            securityResult.setStatusCode(StatusCode.CRITICAL);
                            securityResult.setErrorReason(ErrorReason.USER_PASS_MISMATCH);
                            securityResult.setErrorData(new JAXBElement<String>(this.ErrorData_QNAME,
                                    String.class, SecurityResult.class,
                                    "Authentication failed because of invalid user/password"));
                            LOG.info("Authentication failed because of invalid user/password " +
                                    "after crossing max retry period");
                        } else {

                            securityResult.setToken(new JAXBElement<String>(this.Token_QNAME,
                                    String.class, SecurityResult.class, null));
                            securityResult.setStatusCode(StatusCode.WAIT);
                            securityResult.setErrorReason(ErrorReason.RETRY_COUNT_EXCEEDED);
                            securityResult.setErrorData(new JAXBElement<String>(this.ErrorData_QNAME,
                                    String.class, SecurityResult.class,
                                    "Several invalid attempts made in quick succession"));
                            LOG.info("Several invalid attempts made in quick succession: " + maxAttemptsAllowed);
                        }
                    }
                }
            } else {
                securityResult.setToken(new JAXBElement<String>(this.Token_QNAME,
                        String.class, SecurityResult.class, null));
                securityResult.setStatusCode(StatusCode.CRITICAL);
                securityResult.setErrorReason(ErrorReason.LOGIN_ERROR);
                securityResult.setErrorData(new JAXBElement<String>(this.ErrorData_QNAME,
                        String.class, SecurityResult.class, "invalid credentials"));
                LOG.info("invalid credentials for: " + userName);
            }
            connection.close();
        } catch(ConfigurationException ce){
            LOG.error("Error Getting configuration token: " + ce.getMessage(), ce);
        } catch(SQLException sqe){
            LOG.error( "Error Executing SQL: " + sqe.getMessage(), sqe);
        } catch(Exception e){
            LOG.error("General Error: " + e.getMessage(), e);
        }

        return securityResult;
    }

    /**
     * Mock Implementation of the SubmitDocumentMetadata call.
     *
     * @param token The Security Token passed by LoginUser
     * @param dataId Document's dataId sent by the Export process
     * @param method Indicates the Type of Document Operation - Added/Updated/Deleted
     * @param phlexDocumentId DocumentId from Phlex (USed for Updates and Deletes)
     * @param reason Reason for Export
     * @param documentName Name of the Document
     * @param attributes Document Attributes
     * @return DocumentResult
     */
    public DocumentResult submitDocumentMetadata(String token,
                                                 long dataId,
                                                 SubmitMethod method,
                                                 long phlexDocumentId,
                                                 String reason,
                                                 String documentName,
                                                 ArrayOfAttribute attributes) {

        LOG.info("Executing operation submitDocumentMetadata");

        Object obj = handleExceedConcurrentConnections("submitDocumentMetadata");
        if (obj != null) {
            return (DocumentResult) obj;
        }

        ServletContext srvContext = (ServletContext) context.getMessageContext().get("HTTP.CONTEXT");

        DocumentResult documentResult = new DocumentResult();
        ErrorResponseHandlerUtil errorUtil = ErrorResponseHandlerUtil.getInstance();
        Element rootElement = readHarnessConfig(Constants.TESTHARNESS_CONFIGFILE);
        try
        {
            DatabaseUtil dbutil = DatabaseUtil.getInstance();
            Connection connection = dbutil.getConnection();

            String sleepTimeParam = (String) srvContext.getAttribute(Constants.SLEEP_TIME);
            if (sleepTimeParam == null) {
                throw new Exception("Cannot Fetch " + Constants.SLEEP_TIME + ", Please check HarnessConfig");
            }

            long sleepTime = Long.parseLong(sleepTimeParam);

            Thread.sleep(sleepTime * 1000);
            LOG.info("SLEEP TIME Configured = "+sleepTime);

            boolean isTokenValid = ValidateSessionToken.validateToken(token);
            LOG.info("Is token valid in submitDocumentMetadata? :" + isTokenValid);
            GregorianCalendar gregorianCalendar = new GregorianCalendar();
            gregorianCalendar.setTime(new Date(System.currentTimeMillis()));
            XMLGregorianCalendar xmlGregorianCalendar =
                    DatatypeFactory.newInstance().newXMLGregorianCalendar(gregorianCalendar);

            if (!isTokenValid) {
                documentResult.setStatusCode(StatusCode.fromValue("RETRY_LOGIN"));
                documentResult.setErrorReason(ErrorReason.fromValue("TOKEN_INVALID"));
                documentResult.setErrorData(new JAXBElement<String>(ErrorData_QNAME, String.class, DocumentResult.class, "Token Invalid or Expired"));
                documentResult.setPhlexDocumentId(phlexDocumentId);
                documentResult.setReceiveDate(xmlGregorianCalendar);
            } else {
                StringBuffer testsuiteName = new StringBuffer("TestSuite-");

                NodeList nodelist = rootElement.getChildNodes();
                for(int k=0;k<nodelist.getLength();k++) {
                    if(nodelist.item(k).hasAttributes()) {
                        testsuiteName.append(nodelist.item(k).getAttributes().getNamedItem("name").getNodeValue());
                    }
                }

                Logger RESULTS_LOG = getResultLogger(testsuiteName.toString());
                NodeList testCaseList = rootElement.getElementsByTagName("TestCase");
                boolean metasectionbreakflag = true;
                for(int i=0; i<testCaseList.getLength(); i++){
                    Element el = (Element)testCaseList.item(i);

                    if (getTextValue(el,"Operation").toLowerCase().equalsIgnoreCase("submitdocumentmetadata") && metasectionbreakflag) {
                        if (method.value().equals(getTextValue(el,"Method"))) {
                                LOG.info("SubmitDocumentMetadata: " + getTextValue(el, "Method") +
                                        " configured in HarnessConfig.xml = " +
                                        getTextValue(el, "ErrorResponse") + ", StatusCode: " +
                                        getTextValue(el, "StatusCode") + "\n");

                                String enteredErrorResponse = getTextValue(el, "ErrorResponse");
                                String response = errorUtil.getErrorResponse(enteredErrorResponse);
                                String statusCode = getTextValue(el, "StatusCode");

                                if (response == null) {
                                    LOG.error("Cannot find StatusCode/ErrorResponse/Description " +
                                            "for configured response of: " + getTextValue(el, "ErrorResponse"));
                                    // Setup Defaults since this doesnt exist in spec and we dont want NPE
                                    response = "3,CRITICAL,Unknown Response Code";
                                    statusCode = "CRITICAL";
                                    enteredErrorResponse = "GENERAL_FAILURE";
                                }
                                String[] responseTextSplit = response.split(",");
                                if (responseTextSplit.length < 3) {
                                    LOG.error("Cannot find ErrorCode, ErrorResponse and Data " +
                                            "for configured response of : "  + response);
                                    response = "3,CRITICAL,Unknown Response Code";
                                    statusCode = "CRITICAL";
                                    enteredErrorResponse = "GENERAL_FAILURE";
                                    responseTextSplit = response.split(",");
                                }
                                if (! responseTextSplit[1].equals(statusCode)) {
                                    LOG.error("Error matching provided statuscode with that of specification. " +
                                            "Provided: " + statusCode + ", Specification says: " + responseTextSplit[1]);
                                    try {
                                        StatusCode.fromValue(statusCode);
                                    } catch(IllegalArgumentException iae) {
                                        LOG.error("Invalid Statuscode: " + statusCode + ", sending back as CRITICAL");
                                        statusCode = "CRITICAL";
                                    }
                                }

                                documentResult.setStatusCode(StatusCode.fromValue(statusCode));
                                documentResult.setErrorReason(ErrorReason.fromValue(enteredErrorResponse));
                                documentResult.setErrorData(new JAXBElement<String>(ErrorData_QNAME,
                                        String.class, DocumentResult.class, response.split(",")[2]));
                                documentResult.setPhlexDocumentId(phlexDocumentId);
                                documentResult.setReceiveDate(xmlGregorianCalendar);
                                StringBuffer sBuffer = new StringBuffer("");
                                if (attributes != null) {
                                    List<Attribute> attributeList = attributes.getAttribute();
                                    for (int k = 0; k < attributeList.size(); k++) {
                                        sBuffer.append("Field Type: " + attributeList.get(k).getFieldType().value() + " \nField Name:" +
                                                attributeList.get(k).getFieldName().getValue() + "\nField Value:" + attributeList.get(k).getFieldValue().getValue() + "\n");
                                    }
                                }

                                StringWriter stringWriter = new StringWriter();
                                PrintWriter printer = new PrintWriter(stringWriter, true);
                                printer.println("DataId: " + dataId);
                                printer.println("SubmitMethod: " + method.value());
                                printer.println("PhlexDocumentId: " + phlexDocumentId);
                                printer.println("Reason: " + reason);
                                printer.println("Document Name: " + documentName);
                                printer.println("Attributes: \n" + sBuffer);

                                RESULTS_LOG.info(testsuiteName.toString()+ "::" + getTextValue(el, "Name") + "::" + getTextValue(el, "Operation"));
                                RESULTS_LOG.info("Input Parameters: \n" + stringWriter.toString());

                                stringWriter = new StringWriter();
                                printer = new PrintWriter(stringWriter, true);
                                printer.println("StatusCode: " + documentResult.getStatusCode());
                                printer.println("ErrorReason: " + documentResult.getErrorReason());
                                printer.println("ErrorData: " + documentResult.getErrorData().getValue());
                                printer.println("Date: " + documentResult.getReceiveDate());
                                RESULTS_LOG.info("Output: \n" + stringWriter.toString());
                                metasectionbreakflag = false;
                                break;
                            }
                        }
                    }
                    if(metasectionbreakflag) {
                        LOG.info("Error SubmitDocumentMetadata: " + method.value() +
                            " NOT configured in HarnessTestConfig.xml, Hence values ErrorResponse: null  and StatusCode: null \n");
                    }
                }
            connection.close();
        } catch(SQLException sqe){
            LOG.error("Error Executing SQL: " + sqe.getMessage(), sqe);
        } catch(DatatypeConfigurationException dce){
            LOG.error("DataType Config Error: " + dce.getMessage(), dce);
        } catch(InterruptedException tie){
            LOG.error("Thread Sleep Error: " + tie.getMessage(), tie);
        } catch(Exception ex) {
            LOG.error("General Error: "+ ex.getMessage(), ex);
        }

        return documentResult;
    }

    /**
     * Mock Implementation of the SubmitDocument call.
     *
     * @param token The Security Token passed by LoginUser
     * @param dataId Document's dataId sent by the Export process
     * @param method Indicates the Type of Document Operation - Added/Updated/Deleted
     * @param phlexDocumentId DocumentId from Phlex (USed for Updates and Deletes)
     * @param reason Reason for Export
     * @param fileExtension Extension for the file to be added/updated/deleted
     * @param documentName Name of the Document
     * @param attributes Attributes for the Document
     * @param content Base-64 encoded Document.
     * @return DocumentResult
     */
    public DocumentResult submitDocument(String token,
                                         long dataId,
                                         SubmitMethod method,
                                         Long phlexDocumentId,
                                         String reason,
                                         String fileExtension,
                                         String documentName,
                                         ArrayOfAttribute attributes,
                                         byte[] content) {

        LOG.info("Executing operation submitDocument. Value of Counter: " + Constants.counter.get());

        Object obj = handleExceedConcurrentConnections("submitDocument");
        if (obj != null) {
            return (DocumentResult) obj;
        }

        DocumentResult documentResult = new DocumentResult();
        ErrorResponseHandlerUtil errorUtil = ErrorResponseHandlerUtil.getInstance();
        Element rootElement = readHarnessConfig(Constants.TESTHARNESS_CONFIGFILE);
        ServletContext srvContext = (ServletContext) context.getMessageContext().get("HTTP.CONTEXT");

        try
        {
            DatabaseUtil dbutil = DatabaseUtil.getInstance();
            Connection connection = dbutil.getConnection();

            boolean isTokenValid = ValidateSessionToken.validateToken(token);
            LOG.info("Is token valid in submitDocument? :" + isTokenValid);
            GregorianCalendar gregorianCalendar = new GregorianCalendar();
            gregorianCalendar.setTime(new Date(System.currentTimeMillis()));
            XMLGregorianCalendar xmlGregorianCalendar =
                    DatatypeFactory.newInstance().newXMLGregorianCalendar(gregorianCalendar);

            if (!isTokenValid) {
                documentResult.setStatusCode(StatusCode.fromValue("RETRY_LOGIN"));
                documentResult.setErrorReason(ErrorReason.fromValue("TOKEN_INVALID"));
                documentResult.setErrorData(new JAXBElement<String>(ErrorData_QNAME, String.class, DocumentResult.class, "Token Invalid or Expired"));
                documentResult.setPhlexDocumentId(phlexDocumentId);
                documentResult.setReceiveDate(xmlGregorianCalendar);
            } else {
                StringBuffer testsuiteName = new StringBuffer("TestSuite-");

                NodeList nodelist = rootElement.getChildNodes();
                for(int k=0;k<nodelist.getLength();k++) {
                    if(nodelist.item(k).hasAttributes()) {
                        testsuiteName.append(nodelist.item(k).getAttributes().getNamedItem("name").getNodeValue());
                    }
                }

                Logger RESULTS_LOG = getResultLogger(testsuiteName.toString());

                String sleepTimeParam = (String) srvContext.getAttribute(Constants.SLEEP_TIME);
                if (sleepTimeParam == null) {
                    throw new Exception("Cannot Fetch " + Constants.SLEEP_TIME + ", Please check HarnessConfig");
                }

                long sleepTime = Long.parseLong(sleepTimeParam);

                Thread.sleep(sleepTime * 1000);
                LOG.info("SLEEP TIME Configured = " + sleepTime);

                NodeList testCaseList = rootElement.getElementsByTagName("TestCase");
                boolean documentsectionbreakflag = true;
                for(int i=0; i<testCaseList.getLength(); i++){
                    Element el = (Element)testCaseList.item(i);

                    if (getTextValue(el,"Operation").toLowerCase().equalsIgnoreCase("submitdocument") && documentsectionbreakflag) {
                        if (method.value().equals(getTextValue(el,"Method"))) {
                                LOG.info("SubmitDocument: " + method.value() +
                                        " configured in HarnessTestConfig.cfg = " +
                                        getTextValue(el, "ErrorResponse") + ", StatusCode: " +
                                        getTextValue(el, "StatusCode") + "\n");

                                String enteredErrorResponse = getTextValue(el, "ErrorResponse");
                                String response = errorUtil.getErrorResponse(enteredErrorResponse);
                                String statusCode = getTextValue(el, "StatusCode");

                                if (response == null) {
                                    LOG.error("Cannot find StatusCode/ErrorResponse/Description " +
                                        "for configured response of: " + response);
                                    // Setup Defaults since this doesnt exist in spec and we dont want NPE
                                    response = "3,CRITICAL,Unknown Response Code";
                                    statusCode = "CRITICAL";
                                    enteredErrorResponse = "GENERAL_FAILURE";
                                }

                                String[] responseTextSplit = response.split(",");
                                if (responseTextSplit.length < 3) {
                                    LOG.error("Cannot find ErrorCode, ErrorResponse and Data " +
                                        "for configured response of : "  + response);
                                    // Setup Defaults since this doesnt exist in spec and we dont want NPE
                                    response = "3,CRITICAL,Unknown Response Code";
                                    statusCode = "CRITICAL";
                                    enteredErrorResponse = "GENERAL_FAILURE";
                                    responseTextSplit = response.split(",");
                                }

                                if (! responseTextSplit[1].equals(statusCode)) {
                                    LOG.error("Error matching provided statuscode with that of specification. " +
                                        "Provided: " + statusCode + ", Specification says: " + responseTextSplit[1]);
                                    try {
                                        StatusCode.fromValue(statusCode);
                                    } catch(IllegalArgumentException iae) {
                                        LOG.error("Invalid Statuscode: " + statusCode + ", sending back as CRITICAL");
                                        statusCode = "CRITICAL";
                                    }
                                }

                                documentResult.setStatusCode(StatusCode.fromValue(statusCode));
                                documentResult.setErrorReason(ErrorReason.fromValue(enteredErrorResponse));
                                documentResult.setErrorData(new JAXBElement<String>(ErrorData_QNAME,
                                        String.class, DocumentResult.class, response.split(",")[2]));
                                if (method.value().equals("ADD")) {
                                    PreparedStatement selectAttemptsStatement = connection.prepareStatement(SELECT_SEQ);
                                    ResultSet rs = selectAttemptsStatement.executeQuery();
                                    rs.next();
                                    LOG.info("SubmitDocument:" + method.value() + ": phlexDocumentId =" + rs.getLong(1));
                                    phlexDocumentId = rs.getLong(1);
                                }
                                documentResult.setPhlexDocumentId(phlexDocumentId);
                                documentResult.setReceiveDate(xmlGregorianCalendar);

                                StringBuffer sBuffer = new StringBuffer("");
                                if(attributes != null){
                                    List<Attribute> attributeList = attributes.getAttribute();
                                    for(int k=0;k<attributeList.size();k++)
                                    {
                                        sBuffer.append("Field Type: " + attributeList.get(k).getFieldType().value() +
                                                " \nField Name:" + attributeList.get(k).getFieldName().getValue() +
                                                "\nField Value:" + attributeList.get(k).getFieldValue().getValue() +
                                                "\n");
                                    }
                                }

                                StringWriter stringWriter = new StringWriter();
                                PrintWriter printer = new PrintWriter(stringWriter, true);
                                printer.println("DataId: " + dataId);
                                printer.println("SubmitMethod: " + method.value());
                                printer.println("PhlexDocumentId: " + phlexDocumentId);
                                printer.println("Reason: " + reason);
                                printer.println("FileExtension: "  + fileExtension);
                                printer.println("Document Name: " + documentName);
                                printer.println("Document Size: " + content.length);
                                printer.println("Attributes: \n" + sBuffer);


                                RESULTS_LOG.info(testsuiteName.toString()+"::"+getTextValue(el,"Name")+"::"+getTextValue(el,"Operation"));
                                RESULTS_LOG.info("Input Parameters: \n" + stringWriter.toString());

                                stringWriter = new StringWriter();
                                printer = new PrintWriter(stringWriter, true);
                                printer.println("StatusCode: " + documentResult.getStatusCode());
                                printer.println("ErrorReason: " + documentResult.getErrorReason());
                                printer.println("ErrorData: " + documentResult.getErrorData().getValue());
                                printer.println("Date: " + documentResult.getReceiveDate());
                                RESULTS_LOG.info("Output: \n" + stringWriter.toString());
                                documentsectionbreakflag = false;
                                break;
                        }
                    }
                }
                if(documentsectionbreakflag) {
                    LOG.info("Error SubmitDocument: " + method.value() +
                            " NOT configured in HarnessTestConfig.xml, Hence values ErrorResponse: null  and StatusCode: null \n");
                }
            }
            connection.close();
        } catch(SQLException sqe){
            LOG.error("Error Executing SQL: " + sqe.getMessage(), sqe);
        } catch(DatatypeConfigurationException dce){
            LOG.error("DataType Config Error: " + dce.getMessage(), dce);
        } catch(InterruptedException tie){
            LOG.error("Thread Sleep Error: " + tie.getMessage(), tie);
        } catch(Exception ex) {
            LOG.error("General Error: " + ex.getMessage(), ex);
        }

        return documentResult;
    }

    /**
     * Mock implementation of Logout call
     *
     * @param token Input Token from Login call
     * @return SecurityResult containing information about the token
     */
    public SecurityResult logout(java.lang.String token) {

        Object obj = handleExceedConcurrentConnections("logout");
        if (obj != null) {
            return (SecurityResult) obj;
        }

        SecurityResult securityResult = new SecurityResult();
        Connection connection = null;
        try {
            boolean isTokenValid = ValidateSessionToken.validateToken(token);
            DatabaseUtil dbutil = DatabaseUtil.getInstance();
            connection = dbutil.getConnection();

            if(isTokenValid) {
                PreparedStatement deleteStatement = connection.prepareStatement(DELETE_SESSIONS);
                deleteStatement.setString(1, token);
                int deleteresult = deleteStatement.executeUpdate();
                securityResult.setToken(new JAXBElement<String>(this.Token_QNAME, String.class, SecurityResult.class,null ));
                securityResult.setStatusCode(StatusCode.SUCCESS);
                securityResult.setErrorReason(ErrorReason.NONE);
                LOG.info("SUCCESS. deleted all token matching in Sessions =" + token);
            }else{
                securityResult.setToken(new JAXBElement<String>(this.Token_QNAME, String.class, SecurityResult.class,token ));
                securityResult.setStatusCode(StatusCode.CRITICAL);
                securityResult.setErrorReason(ErrorReason.TOKEN_INVALID);
                LOG.info("FAILURE. deleted token does not match in Sessions =" + token);
            }
        } catch(SQLException sqe){
            LOG.error( "Error Executing SQL: " + sqe.getMessage(), sqe);
            securityResult.setToken(new JAXBElement<String>(this.Token_QNAME, String.class, SecurityResult.class,token ));
            securityResult.setStatusCode(StatusCode.RETRY);
            securityResult.setErrorReason(ErrorReason.DATABASE_ERROR);
            LOG.info("DB-ERROR. while trying to delete token from Sessions =" + token);
        }finally {
            try {
                connection.close();
            }catch (SQLException sqe){
                LOG.error( "Error connecting to DB: " + sqe.getMessage(), sqe);
            }
        }

        return securityResult;
    }

    private Element readHarnessConfig(String harnessconfigfile){
        DocumentBuilderFactory builderFactory =  DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        Element rootElement = null;
        try{
            builder = builderFactory.newDocumentBuilder();
            Document document = builder.parse(new FileInputStream(harnessconfigfile));
            rootElement = document.getDocumentElement();
        } catch(ParserConfigurationException ce){
            LOG.error("Error Getting configuration token: " + ce.getMessage(), ce);
        } catch(SAXException spe) {
            LOG.error("Error Getting at parser: " + spe.getMessage(), spe);
        } catch(FileNotFoundException fne){
            LOG.error("Harness Configuration File Not Found: " + fne.getMessage(), fne);
        } catch(IOException ioe){
            LOG.error("IO Exception Error: " + ioe.getMessage(), ioe);
        }

        return rootElement;
    }

    /**
     * Method to fetch the Result Logger given the test suite name.
     *
     * @param testsuitename Name of testsuite used to name the logs
     * @return Logger object
     */
    private synchronized Logger getResultLogger(String testsuitename) {

        Logger resultlogger = null;
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            ContextInitializer ci = new ContextInitializer(context);
            context.reset();
            MDC.put("testsuite-name", testsuitename);
            ci.autoConfig();
            resultlogger = context.getLogger("testresults");
        }catch(JoranException joe){
            LOG.error("Exception in getResultLogger : " + joe.getMessage(), joe);
        }

        return resultlogger;
    }

    /**
     * This method is not used (But kept for reference) since this seems to block the
     * other loggers from activating itself after a reset. Use the above getResultLogger
     *
     * @param testsuitename Name of the testsuite used to name logfile
     * @return Logger Object
     */
    private synchronized Logger getResultLogger1(String testsuitename){
        Logger resultLogger = null;

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        FileAppender fileAppender = new FileAppender();
        fileAppender.setContext(context);
        fileAppender.setName("resultslogger");
        fileAppender.setAppend(true);
        fileAppender.setFile(System.getProperty("catalina.base") + File.separator + "logs" + File.separator + "results_" + testsuitename + ".log");

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d [%thread] %-5level %logger{35} [%M]- %msg %n");
        if (!encoder.isStarted()) {
            encoder.start();
        }
        fileAppender.setEncoder(encoder);

        resultLogger = context.getLogger("resultslogger");
        if (! fileAppender.isStarted()) {
            fileAppender.start();
            resultLogger.addAppender(fileAppender);
        }

        LOG = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(PhlexViewServiceImpl.class);

        return resultLogger;
    }

    /**
     * Fetch the text value from XML given the root element
     *
     * @param ele Root Configuration Element
     * @param tagName Tag for which value needs to be obtained
     * @return Value for the tag
     */
    private String getTextValue(Element ele, String tagName) {
        String textVal = null;
        NodeList nl = ele.getElementsByTagName(tagName);
        if(nl != null && nl.getLength() > 0) {
            Element el = (Element)nl.item(0);
            textVal = el.getFirstChild().getNodeValue();
        }
        return textVal;
    }

    /**
     * Check if the max concurrent connections have exceeded.
     *
     * @param method which method are we processing now
     * @return SecurityResult or DocumentResult based on the method
     */
    private Object handleExceedConcurrentConnections(String method) {
        Boolean exceededValue = (Boolean) this.context.getMessageContext().get("CONNECTION_EXCEEDED");
        if (exceededValue == null) {
            return null;        // No problem with concurrent connections
        }
        if (exceededValue.booleanValue()) {
            LOG.info("Connection Exceeded Caught in PhlexService for method: " + method);
            if ("loginUser".equals(method) || "logout".equals(method)) {
                SecurityResult securityResult = new SecurityResult();
                securityResult.setToken(new JAXBElement<String>(this.Token_QNAME, String.class, SecurityResult.class,null));
                securityResult.setStatusCode(StatusCode.CRITICAL);
                securityResult.setErrorReason(ErrorReason.GENERAL_FAILURE);

                return securityResult;
            }

            DocumentResult documentResult = new DocumentResult();
            documentResult.setStatusCode(StatusCode.CRITICAL);
            documentResult.setErrorReason(ErrorReason.GENERAL_FAILURE);
            documentResult.setErrorData(new JAXBElement<String>(ErrorData_QNAME,
                    String.class, DocumentResult.class, "Concurrent Connections Exceeded the maximum limit"));
            return documentResult;
        }

        return null;

    }
}
