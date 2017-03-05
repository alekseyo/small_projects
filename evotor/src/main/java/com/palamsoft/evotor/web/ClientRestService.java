package com.palamsoft.evotor.web;

import java.math.BigDecimal;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;

import com.palamsoft.evotor.model.AddClientRequest;
import com.palamsoft.evotor.model.AddClientResponse;
import com.palamsoft.evotor.model.Client;
import com.palamsoft.evotor.model.ClientBalanceRequest;
import com.palamsoft.evotor.model.ClientBalanceResponse;
import com.palamsoft.evotor.service.ClientService;
import com.palamsoft.evotor.service.ExistingCustomerException;
import com.palamsoft.evotor.service.NoSuchCustomerException;
import com.palamsoft.evotor.service.ServiceException;
import com.palamsoft.evotor.service.WrongPasswordException;

@Path("/client")
public class ClientRestService {
	private final Logger logger = Logger.getLogger(this.getClass());

	@javax.ws.rs.core.Context 
	private ServletContext context;
	
	private ClientService service;
	
	@PostConstruct
	public void init() {
		service = (ClientService) context.getAttribute(ContextLoaderListener.CLIENT_SERVICE_KEY);
		if (service == null) {
			throw new ServiceException("Cannot load ClientService");
		}
	}
	
	@POST
	@Path("/add")
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public AddClientResponse addClient( AddClientRequest request ) {
		
		Client client = new Client();
		client.setLogin( request.getLogin() );
		client.setPassword( request.getPassword() );
		
		logger.info("Add client: " + client.getLogin());
		
		AddClientResponse response = new AddClientResponse();
		try {
			service.addClient(client);
			response.setResultCode(0);
		}
		catch (ExistingCustomerException e) {
			logger.warn("Customer already exists: " + e.getMessage());
			response.setResultCode(1);
		}
		catch (ServiceException e) {
			logger.warn("Unable to add customer: " + e.getMessage());
			response.setResultCode(2);
		}
		catch (Throwable t) {
			logger.error("Failed to add customer", t);
			response.setResultCode(2);
		}
		return response;
	}
	
	@POST
	@Path("/balance")
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_XML)
	public ClientBalanceResponse getBalance( ClientBalanceRequest request ) {
		Client client = new Client();
		client.setLogin( request.getLogin() );
		client.setPassword( request.getPassword() );
		
		logger.info("Get balance for client: " + client.getLogin());
		ClientBalanceResponse response = new ClientBalanceResponse();
		try {
			BigDecimal balance = service.getBalance(client.getLogin(), client.getPassword());
			logger.debug("Balance: " + balance);
			response.setBalance( balance );
			response.setResultCode(0);
		}
		catch (NoSuchCustomerException e) {
			logger.warn("Refused to check balance: " + e.getMessage());
			response.setResultCode(3);
		}
		catch (WrongPasswordException e) {
			logger.warn("Refused to check balance: " +  e.getMessage());
			response.setResultCode(4);
		}
		catch (ServiceException e) {
			logger.warn("Unable to get balance for customer: " + e.getMessage());
			response.setResultCode(2);
		}
		catch (Throwable t) {
			logger.error("Failed to check customer", t);
			response.setResultCode(2);
		}

		return response;
	}
	
	
}
