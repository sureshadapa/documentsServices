package com.quintiles.biibexport.util;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;

/**
 * Out Interceptor that decrements the concurrency count when we leave the service
 */
public class OutCounterInterceptor extends AbstractPhaseInterceptor {

    @Context
    ServletContext ctx;

    private static String smThreadCount = "threadCount";
    private Logger logger = LoggerFactory.getLogger("interceptors");

    public OutCounterInterceptor() {
        super(Phase.WRITE);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        int counter = Constants.counter.decrementAndGet();
        logger.debug("Counter in OutInterceptor: " + counter);
    }
}