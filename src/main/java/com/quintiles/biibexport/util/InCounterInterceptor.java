package com.quintiles.biibexport.util;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;

/**
 * In Interceptor that decrements the concurrency count when we enter the service.
 *
 * Sets a variable on the message context if it exceeds the maximum value.
 */
public class InCounterInterceptor extends AbstractPhaseInterceptor {

    private Logger logger = LoggerFactory.getLogger("interceptors");

    private static String smThreadCount = "threadCount";

    public InCounterInterceptor() {
        super(Phase.UNMARSHAL);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        ServletContext ctx = (ServletContext) message.get("HTTP.CONTEXT");
        String concurrentConnValue = (String) ctx.getAttribute(Constants.CONCURRENT_CONNECTIONS);
        if (concurrentConnValue == null) {
            throw new Fault(
                    new Exception("Cannot Fetch " + Constants.CONCURRENT_CONNECTIONS + ", Please check HarnessConfig"));
        }
        int concurrentConnections = Integer.parseInt(concurrentConnValue);

        int counter = Constants.counter.getAndIncrement();
        logger.debug("Counter in InInterceptor: " + counter);
        if (counter > concurrentConnections) {
            logger.info("Connection Exceeded");
            message.put("CONNECTION_EXCEEDED", Boolean.TRUE);
        } else {
            message.put("CONNECTION_EXCEEDED", Boolean.FALSE);
        }
    }

}