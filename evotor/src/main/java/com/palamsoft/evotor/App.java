package com.palamsoft.evotor;

import java.io.File;
import java.io.IOException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main(String[] args) throws Exception {
    	/*
        JAXBContext jc = JAXBContext.newInstance(Response.class);
        
 
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        Response response = (Response) unmarshaller.unmarshal(
        		App.class.getClassLoader().getResourceAsStream("response.xml") );
 
        response.setBalance(null);
        Marshaller marshaller = jc.createMarshaller();
        
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(response, System.out);
        
    	
        Class[] classes = new Class[3]; 
        classes[0] = com.palamsoft.evotor.model.Address.class; 
        classes[1] = com.palamsoft.evotor.model.Customer.class; 
        classes[2] = com.palamsoft.evotor.model.PhoneNumber.class; 
        JAXBContext jaxbContext = JAXBContext.newInstance(classes);
         
        SchemaOutputResolver sor = new MySchemaOutputResolver();
        jaxbContext.generateSchema(sor);
          */
         
    }
    private static class MySchemaOutputResolver extends SchemaOutputResolver {
        
        public Result createOutput(String uri, String suggestedFileName) throws IOException {
           File file = new File(suggestedFileName);
           StreamResult result = new StreamResult(file);
           result.setSystemId(file.toURI().toURL().toString());
           return result;
        }
      
     }
}
