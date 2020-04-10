package com.quintiles.biibexport.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ErrorResponseHandler Utility class to get Error Responses
 */
public class ErrorResponseHandlerUtil {
	
	private static final Logger LOG = Logger.getLogger(ErrorResponseHandlerUtil.class.getName());
    private final static String ERRORHANDLING_CONFIGFILE = System.getProperty("catalina.base") + File.separator + "conf"+ File.separator +"ErrorHandlingResponse.csv";
	
    private static ErrorResponseHandlerUtil instance;
    private Map<String, String> map = new HashMap<>();
    private InputStream inputStream = null;
    
    private ErrorResponseHandlerUtil() {
		try {
            inputStream = new FileInputStream(ERRORHANDLING_CONFIGFILE);

			if (inputStream == null) {
				LOG.info("Sorry, unable to find ErrorHandlingResponse File");
				return;
			}
		} catch (Exception ex) {
            LOG.log(Level.SEVERE, "Error Reading ErrorResponse File: " + ex.getMessage(), ex);
            throw new IllegalArgumentException("Error Reading File: " + ex.getMessage());
		}
		
        Properties props = new Properties();
        try {
            props.load(inputStream);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error Loading Properties file: " + ex.getMessage(), ex);
            throw new IllegalArgumentException("Error Loading Properties file: " + ex.getMessage());
        }

        Enumeration<?> e = props.propertyNames();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			String value = props.getProperty(key);
			map.put(key.split("=")[0], value);
		}
    }

    /**
     * Get Singleton Instance
     *
     * @return ErrorResponseHandlerUtil instance.
     */
    public static ErrorResponseHandlerUtil getInstance() {
        if (instance == null) {
            instance = new ErrorResponseHandlerUtil();
        }
        return instance;
    }

    /**
     * Get Error Response for the given Error Code
     *
     * @param key Errorcode for which response is requested
     * @return The Error response for the given code.
     *
     */
    public String getErrorResponse(String key) {
        if(map.get(key) != null) {
        	return map.get(key);
        }else{
	        LOG.info("Invalid ErrorReason Code: " + key);
        	return "Invalid ErrorReason Code:" + key;
    	}
    }

    /**
     * Standalone Test method.
     *
     * @param s
     */
    public static void main(String s[]){
    	ErrorResponseHandlerUtil errorutil = ErrorResponseHandlerUtil.getInstance();
		System.out.println(errorutil.getErrorResponse("LOGIN_FAILURE"));
		System.out.println(errorutil.getErrorResponse("SURESH"));
    }
}
