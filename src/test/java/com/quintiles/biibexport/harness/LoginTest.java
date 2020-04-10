package com.quintiles.biibexport.harness;

import org.junit.Test;
import static org.junit.Assert.*;


public class LoginTest extends BaseTest {
	
    @Test
    public void testSuccessfulLogin() throws Exception {
	    
        SecurityResult result = getService().loginUser("suresh", "systems", "sureship");
        assertNotNull(result);
        assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        assertEquals(ErrorReason.NONE, result.getErrorReason());

    }

    @Test
    public void testInvalidUser() throws Exception {
        SecurityResult result = getService().loginUser("invalid", "invalid", "invalid");
        assertNotNull(result);
        assertEquals(StatusCode.CRITICAL, result.getStatusCode());
        assertEquals(ErrorReason.LOGIN_ERROR, result.getErrorReason());
    }

    @Test
    public void testMaxRetries() throws Exception {
        // HarnessConfig needs to be setup with the following parameters:
        // Max Allowed Retries: 2 and Time between retries: 30

        // First Attempt
        SecurityResult result = getService().loginUser("suresh", "invalid", "sureship");
        assertNotNull(result);
        assertEquals(StatusCode.CRITICAL, result.getStatusCode());
        assertEquals(ErrorReason.USER_PASS_MISMATCH, result.getErrorReason());

        // Second Attempt
        SecurityResult result2 = getService().loginUser("suresh", "invalid", "sureship");
        assertNotNull(result2);
        assertEquals(StatusCode.CRITICAL, result2.getStatusCode());
        assertEquals(ErrorReason.USER_PASS_MISMATCH, result2.getErrorReason());

        // Third Attempt -- Should result in retry count exceeded
        SecurityResult result3 = getService().loginUser("suresh", "invalid", "sureship");
        assertNotNull(result3);
        assertEquals(StatusCode.WAIT, result3.getStatusCode());
        assertEquals(ErrorReason.RETRY_COUNT_EXCEEDED, result3.getErrorReason());

    }
}
