package com.palamsoft.evotor.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Logger;

import com.palamsoft.evotor.dao.JdbcDao;
import com.palamsoft.evotor.service.ClientServiceImpl;
import com.palamsoft.evotor.service.ServiceException;

public class ContextLoaderListener implements ServletContextListener {
	
	private final Logger logger = Logger.getLogger(this.getClass());
	
	public static final String CLIENT_SERVICE_KEY = "clientService";
	public static final String DB_PROPERTIES_KEY = "db";
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		ServletContext context = sce.getServletContext();
		
		ClientServiceImpl clientService = new ClientServiceImpl();
		JdbcDao dao = new JdbcDao( newDataSource(context) );
		clientService.setDao(dao);
		
		context.setAttribute(CLIENT_SERVICE_KEY, clientService);
		
	}
	
	private DataSource newDataSource(ServletContext context) {
		String dbPropertiesFile = context.getInitParameter(DB_PROPERTIES_KEY);
		logger.debug( "db properties file: " + dbPropertiesFile);
		final Properties dbProperties = new Properties();
		try (final InputStream stream =
		           this.getClass().getResourceAsStream(dbPropertiesFile) ) {
		    if (stream == null) {
		    	throw new ServiceException("No DB properties found");
		    }
			dbProperties.load(stream);
		} catch (IOException e) {
			throw new ServiceException("Failed to read database properties", e);
		}
		
		String username = dbProperties.getProperty("db.username");
		String password = dbProperties.getProperty("db.password");
		String url = dbProperties.getProperty("db.url");
		String driver = dbProperties.getProperty("db.driver");
		
		BasicDataSource dbcp = new BasicDataSource();
		dbcp.setDriverClassName(driver);
		dbcp.setUsername(username);
		dbcp.setPassword(password);
		dbcp.setUrl(url);
		dbcp.setDefaultAutoCommit(false);
		

		return dbcp;
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		
	}

}
