package com.quintiles.biibexport.harness;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.MalformedURLException;
import java.net.URL;

public class BaseTest {
    private IExternalConnector connector;

    public BaseTest() {
        URL wsdl = null;
        try {
            wsdl = new URL("http://localhost:8080/testharness/services/quintiles?wsdl");
        } catch(MalformedURLException mue) {
            throw new IllegalArgumentException();
        }

        QName serviceQ = new QName("http://www.phlexeview.com/", "IExternalConnectorService");
        Service service = Service.create(wsdl, serviceQ);
        this.connector = service.getPort(IExternalConnector.class);
    }

    public IExternalConnector getService() {
        return this.connector;
    }
}
