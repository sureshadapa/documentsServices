package com.quintiles.biibexport.harness;

import org.junit.Test;
import static org.junit.Assert.*;

public class LogoutTest extends BaseTest {
    @Test
    public void testLogoutBare() throws Exception {
        SecurityResult securityResult = getService().loginUser("suresh", "systems", "");

        SecurityResult result = getService().logout(securityResult.getToken().getValue());
        assertNotNull(result);
        assertEquals(StatusCode.SUCCESS, result.getStatusCode());
        assertEquals(ErrorReason.NONE, result.getErrorReason());
    }
}
