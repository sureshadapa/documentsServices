package com.quintiles.biibexport.util;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Store all Constants for the TestHarness configuration.
 */
public class Constants {
    public final static String TIME_BETWEEN_RETRIES = "timeBetweenRetries";
    public final static String SESSION_TIMEOUT = "sessionTimeout";
    public final static String MAX_INVALID_ATTEMPTS = "maxInvalidAttempts";
    public final static String SLEEP_TIME = "sleepTime";
    public final static String CONCURRENT_CONNECTIONS = "concurrentConnections";

    public final static String TESTHARNESS_CONFIGFILE = System.getProperty("catalina.base") + File.separator +"HarnessConfig.xml";

    // Counter to store the maximum number of concurrent connections.
    public static AtomicInteger counter = new AtomicInteger(1);
}
