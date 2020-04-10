package com.quintiles.biibexport.harness;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import com.quintiles.biibexport.harness.SubmitMethod;
import java.util.List;
import com.quintiles.biibexport.harness.ArrayOfAttribute;
import com.quintiles.biibexport.harness.Attribute;
import com.quintiles.biibexport.harness.FieldType;
import com.quintiles.biibexport.harness.ObjectFactory;

public class SubmitDocumentMetadataTest extends BaseTest {

    @Test
    public void testSubmitDocumentMetadataBare() throws Exception {
    SecurityResult securityResult = getService().loginUser("suresh", "systems", "");

        if (StatusCode.SUCCESS.equals(securityResult.getStatusCode())) {
    
            ArrayOfAttribute attrs = new ArrayOfAttribute();
            List<Attribute> list = attrs.getAttribute();
            ObjectFactory ObjFac = new ObjectFactory();
            Attribute[] att = new Attribute[3];
            att[0] = new Attribute();
            att[0].setFieldType(FieldType.fromValue("Text"));
            att[0].setFieldName(ObjFac.createAttributeFieldName("Study of Drug"));
            att[0].setFieldValue(ObjFac.createAttributeFieldValue("Simvastatin + niacin 15 + 0.5 mg tab"));
        
            att[1] = new Attribute();
            att[1].setFieldType(FieldType.fromValue("Text"));
            att[1].setFieldName(ObjFac.createAttributeFieldName("Pilot Study Conducted by Company at Location"));
            att[1].setFieldValue(ObjFac.createAttributeFieldValue("LUPIN,Pune,India"));
        
            att[2] = new Attribute();
            att[2].setFieldType(FieldType.fromValue("Text"));
            att[2].setFieldName(ObjFac.createAttributeFieldName("Compliance"));
            att[2].setFieldValue(ObjFac.createAttributeFieldValue("21 CFR Part 11 guidelines"));       
        
            list.add(att[0]);
            list.add(att[1]);
            list.add(att[2]);

            DocumentResult result = getService().submitDocumentMetadata(securityResult.getToken().getValue(),
                new Long(101), SubmitMethod.fromValue("UPDATE"),new Long(213), "Validate Pilot Study at Lupin", "Simvastatin_Study", attrs);
            assertNotNull(result);

            DocumentResult result2 = getService().submitDocumentMetadata(securityResult.getToken().getValue(),
                    new Long(101), SubmitMethod.fromValue("DELETE"),new Long(213), "remove Pilot Study", "Simvastatin_Study", attrs);
            assertNotNull(result2);
        }
    }
}
