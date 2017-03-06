package com.palamsoft.evotor.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.palamsoft.evotor.model.AddClientResponse;
import com.palamsoft.evotor.model.Client;
import com.palamsoft.evotor.model.ClientBalanceResponse;
import com.palamsoft.evotor.service.ClientService;
import com.palamsoft.evotor.service.ExistingCustomerException;
import com.palamsoft.evotor.service.NoSuchCustomerException;
import com.palamsoft.evotor.service.ServiceException;
import com.palamsoft.evotor.service.WrongPasswordException;

public class ClientServlet extends HttpServlet {
	private final Logger logger = Logger.getLogger(this.getClass());

	private ClientService service;

	@Override
	public void init() throws ServletException {
		service = (ClientService) this.getServletContext().getAttribute(ContextLoaderListener.CLIENT_SERVICE_KEY);
		if (service == null) {
			throw new ServiceException("Cannot load ClientService");
		}
	}

	@Override
	protected void doPost(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
			throws ServletException, IOException {

		Element root;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(servletRequest.getInputStream());

			root = doc.getDocumentElement();
			if (!root.getTagName().equals("request")) {
				logger.info("Invalid request: document root is not a \"request\"");
				servletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
		} catch (ParserConfigurationException | SAXException e) {
			logger.info("Failed to read request: " + e.getMessage());
			servletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		NodeList requestTypeNodes = root.getElementsByTagName("request-type");
		if (requestTypeNodes.getLength() != 1) {
			logger.info("Invalid request: no \"request-type\" element");
			servletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		RequestHandler handler;
		try {
			String requestType = ((Element) requestTypeNodes.item(0)).getTextContent();

			if (logger.isInfoEnabled()) {
				logger.info("Request with type: " + requestType);
			}
			switch (requestType) {
			case "CREATE-ACT":
				handler = new AddClientHandler();
				break;
			case "GET-BALANCE":
				handler = new GetBalanceHandler();
				break;
			default:
				logger.info("Invalid request type: " + requestType);
				servletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
		} catch (Throwable t) {
			logger.error("Failed to process request", t);
			servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}

		try {
			Object response = handler.handle(root);
			JAXBContext jc = JAXBContext.newInstance(response.getClass());
			Marshaller marshaller = jc.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(response, servletResponse.getOutputStream());
			servletResponse.setStatus(HttpServletResponse.SC_OK);
		} catch (Throwable t) {
			logger.error("Failed to generate response", t);
			servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	private interface RequestHandler {
		
		/**
		 * Performs actual work to process request
		 * 
		 * @param root element of request XML document
		 * @return response object
		 */
		Object handle(Element root);
	}

	private class AddClientHandler implements RequestHandler {

		@Override
		public Object handle(Element root) {
			NodeList extras = root.getElementsByTagName("extra");

			Client client = new Client();
			for (int i = 0; i < extras.getLength(); i++) {
				Element e = (Element) extras.item(i);

				switch (e.getAttribute("name")) {
				case "login":
					client.setLogin(e.getTextContent());
					break;
				case "password":
					client.setPassword(e.getTextContent());
					break;
				}
			}
			
			AddClientResponse response = new AddClientResponse();
			try {
				logger.info("Add customer with login: " + client.getLogin());
				service.addClient(client);
				response.setResultCode(0);
			} catch (ExistingCustomerException e) {
				logger.warn("Unable to add customer: " + e.getMessage());
				response.setResultCode(1);
			} catch (ServiceException e) {
				logger.warn("Unable to add customer: " + e.getMessage());
				response.setResultCode(2);
			}
			catch (Throwable t) {
				logger.error("Error during customer creation", t);
				response.setResultCode(2);
			}
			return response;
		}

	}

	private class GetBalanceHandler implements RequestHandler {

		@Override
		public Object handle(Element root) {
			NodeList extras = root.getElementsByTagName("extra");
			Client client = new Client();

			for (int i = 0; i < extras.getLength(); i++) {
				Element e = (Element) extras.item(i);

				switch (e.getAttribute("name")) {
				case "login":
					client.setLogin(e.getTextContent());
					break;
				case "password":
					client.setPassword(e.getTextContent());
					break;
				}
			}
			ClientBalanceResponse response = new ClientBalanceResponse();

			try {
				logger.info("Check balance for customer: " + client.getLogin());
				response.setBalance(service.getBalance(client.getLogin(), client.getPassword()));
				response.setResultCode(0);
			} catch (NoSuchCustomerException e) {
				logger.warn("Refused to check balance: " + e.getMessage());
				response.setResultCode(3);
			} catch (WrongPasswordException e) {
				logger.warn("Refused to check balance: " + e.getMessage());
				response.setResultCode(4);
			} catch (ServiceException e) {
				logger.warn("Unable to get balance for customer: " + e.getMessage());
				response.setResultCode(2);
			}
			catch (Throwable t) {
				logger.info("Error during customer balance check", t);
				response.setResultCode(2);
			}
			return response;
		}

	}

}
