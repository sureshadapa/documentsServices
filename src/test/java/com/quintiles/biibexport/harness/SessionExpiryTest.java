package com.quintiles.biibexport.harness;

import org.junit.Test;
import static org.junit.Assert.*;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import com.quintiles.biibexport.harness.SubmitMethod;
import com.quintiles.biibexport.harness.ErrorReason;

import java.util.List;

import com.quintiles.biibexport.harness.ArrayOfAttribute;
import com.quintiles.biibexport.harness.Attribute;
import com.quintiles.biibexport.harness.FieldType;
import com.quintiles.biibexport.harness.ObjectFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Test Session Expiry by setting HarnessConfig.xml to be a low limit and chcking if the second
 * call actually failed the login process.
 */
public class SessionExpiryTest extends BaseTest {
    @Test
    public void testSubmitDocument() throws Exception {
        SecurityResult securityResult = getService().loginUser("suresh", "systems", "");

        if (StatusCode.SUCCESS.equals(securityResult.getStatusCode())) {
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
       
        
            DocumentResult result = getService().submitDocument(securityResult.getToken().getValue(),
                    new Long(101), SubmitMethod.fromValue("ADD"), new Long(213), "Pilot Study Document Validation", "doc","Simvastatin_Study", attrs, fileContent);
            assertNotNull(result);
       
            Thread.sleep(90000);
 
            DocumentResult result2 = getService().submitDocument(securityResult.getToken().getValue(),
                    new Long(101), SubmitMethod.fromValue("UPDATE"), new Long(213), null, null, null, null, fileContent);
            assertNotNull(result2);
            assertEquals("Session should have expired. Check if HarnessConfig has sessionTimeout < 60 secs", ErrorReason.TOKEN_INVALID, result2.getErrorReason());
            
        }
    }
}
