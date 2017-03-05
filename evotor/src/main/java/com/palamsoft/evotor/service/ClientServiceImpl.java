package com.palamsoft.evotor.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

import com.palamsoft.evotor.dao.DataAccessException;
import com.palamsoft.evotor.dao.JdbcDao;
import com.palamsoft.evotor.model.Client;

public class ClientServiceImpl implements ClientService {
	
	private JdbcDao dao;

	@Override
	public void addClient(Client client) {
		if (client.getLogin() == null || client.getLogin().trim().equals("")) {
			throw new ServiceException("Empty client login");
		}
		if (client.getPassword() == null || client.getPassword().trim().equals("")) {
			throw new ServiceException("Empty password");
		}
		
		executeInTransaction(new TransactionCallback<Void>() {

			@Override
			public Void execute(Connection con) throws SQLException {
				Client existing = dao.getClient(con, client.getLogin());
				if (existing != null) {
					throw new ExistingCustomerException("Customer already exists: " + client.getLogin());
				}
				dao.insertClient( con, client );
				return null;
			}
		});
	}

	@Override
	public BigDecimal getBalance(String clientLogin, String password) {
		if (clientLogin == null || clientLogin.trim().equals("")) {
			throw new ServiceException("Empty client login");
		}
		if (password == null || password.trim().equals("")) {
			throw new ServiceException("Empty password");
		}
		
		Client stored = executeInTransaction(new TransactionCallback<Client>() {

			@Override
			public Client execute(Connection con) throws SQLException {
				return dao.getClient(con, clientLogin);
			}
		});
		
		if (stored == null) {
			throw new NoSuchCustomerException("Customer with login: " + clientLogin + " does not exist");
		}
		if ( !stored.getPassword().equals( password ) ) {
			throw new WrongPasswordException( "Wrong password for customer: " + stored.getLogin() );
		}
		return stored.getBalance();
	}

	public void setDao(JdbcDao dao) {
		this.dao = dao;
	}
	
	private interface TransactionCallback<T> {
		T execute(Connection con) throws SQLException;
	}
	
	private <T> T executeInTransaction(TransactionCallback<T> job) {
		try ( Connection con = dao.getConnection() ) {
			try {
				T result = job.execute(con);
				con.commit();
				return result;
			}
			catch (ServiceException e) {
				con.rollback();
				throw e;
			}
			catch (Throwable t) {
				con.rollback();
				throw new DataAccessException("Failed to execute transaction", t);
			}
		} catch (SQLException e) {
			throw new DataAccessException("Failed to close DB connection", e);
		} 
	}

}
