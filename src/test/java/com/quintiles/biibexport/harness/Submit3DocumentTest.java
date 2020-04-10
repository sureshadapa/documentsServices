package com.quintiles.biibexport.harness;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test class to show that sending more than max concurrent connections results in a CRITICAL Status.
 * Make sure that HarnessConfig.xml has a concurrentConnections value set to 3 (This means 3 parallel
 * connections allowed -- starting at 0, 1, 2 .
 *
 * The fourth thread will have a statuscode of CRITICAL.
 *
 *  Instead of running it as parallel requests using maven, this uses Executors to spawn parallel threads
 *  and verify that the results are as expected
 */
public class Submit3DocumentTest extends BaseTest {
    @Test
    public void testSubmitDocument1() throws Exception {
        SecurityResult securityResult = getService().loginUser("suresh", "systems", "");

        ExecutorService executors = Executors.newFixedThreadPool(4);
        Future thread1 = executors.submit(new SubmitDocumentTask("T1", securityResult.getToken().getValue(), StatusCode.SUCCESS));
        Thread.sleep(2000);

        Future thread2 = executors.submit(new SubmitDocumentTask("T2", securityResult.getToken().getValue(), StatusCode.SUCCESS));
        Thread.sleep(2000);
        Future thread3 = executors.submit(new SubmitDocumentTask("T3", securityResult.getToken().getValue(), StatusCode.SUCCESS));
        Thread.sleep(2000);
        Future thread4 = executors.submit(new SubmitDocumentTask("T4", securityResult.getToken().getValue(), StatusCode.CRITICAL));

        executors.shutdown();

        try {
            executors.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch(InterruptedException ie) {}

        thread1.get();
        thread2.get();
        thread3.get();
        thread4.get();
    }


    private class SubmitDocumentTask implements Runnable {
        private StatusCode status;
        private String threadId;
        private String token;

        public SubmitDocumentTask(String id, String token, StatusCode status) {
            this.status = status;
            this.threadId = id;
            this.token = token;
        }

        public void run() {
            ArrayOfAttribute attrs = new ArrayOfAttribute();
            List<Attribute> list = attrs.getAttribute();
            ObjectFactory ObjFac = new ObjectFactory();
            Attribute[] att = new Attribute[3];
            att[0] = new Attribute();
            att[0].setFieldType(FieldType.fromValue("Text"));
            att[0].setFieldName(ObjFac.createAttributeFieldName("To Department"));
            att[0].setFieldValue(ObjFac.createAttributeFieldValue("SAS"));

            att[1] = new Attribute();
            att[1].setFieldType(FieldType.fromValue("Text"));
            att[1].setFieldName(ObjFac.createAttributeFieldName("Email Contact"));
            att[1].setFieldValue(ObjFac.createAttributeFieldValue("ravishankar@lupin.com"));

            att[2] = new Attribute();
            att[2].setFieldType(FieldType.fromValue("Text"));
            att[2].setFieldName(ObjFac.createAttributeFieldName("Reference"));
            att[2].setFieldValue(ObjFac.createAttributeFieldValue("Dr. Kasi Battala RaviShankar"));

            list.add(att[0]);
            list.add(att[1]);
            list.add(att[2]);

            String dummyContent = "This is content from a test file";
            byte[] fileContent = dummyContent.getBytes();


            DocumentResult result = getService().submitDocument(token,
                    new Long(101), SubmitMethod.fromValue("ADD"), new Long(213), "Pilot Study Document Validation", "doc","Simvastatin_Study", attrs, fileContent);
            assertNotNull(result);
            assertEquals("In " + this.threadId, this.status, result.getStatusCode());

        }
    }
}
