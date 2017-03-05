package com.palamsoft.evotor.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.palamsoft.evotor.model.Client;

public class JdbcDao {
	private final DataSource dataSource;
	
	private final String GENERATE_CLIENT_ID = "SELECT seq_client.nextval FROM dual";
	private final String INSERT_CLIENT = "INSERT INTO client(id, login, password, balance) VALUES(?, ?, ?, ?)";
	private final String SELECT_CLIENT = "SELECT id, login, password, balance FROM client WHERE login = ?";

	public JdbcDao(DataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	public Connection getConnection() {
		try {
			return dataSource.getConnection();
		} catch (SQLException e) {
			throw new DataAccessException("Failed to create connection", e);
		}
	}
	
	public Client getClient(Connection conn, String login) throws SQLException {
		try ( PreparedStatement ps = conn.prepareStatement(SELECT_CLIENT) ) {
			ps.setString(1, login);
			try ( ResultSet rs = ps.executeQuery() ) {
				if (!rs.next()) {
					return null;
				}
				Client client = new Client();
				client.setId( rs.getInt(1) );
				client.setLogin( rs.getString(2) );
				client.setPassword( rs.getString(3) );
				client.setBalance( rs.getBigDecimal(4) );
				return client;
			}
		}
	}
	
	public void insertClient(Connection conn, Client client) throws SQLException {
		int newClientId = generateId(conn);
		try ( PreparedStatement ps = conn.prepareStatement(INSERT_CLIENT) ) {
			ps.setInt(1, newClientId);
			ps.setString(2, client.getLogin());
			ps.setString(3, client.getPassword());
			ps.setBigDecimal(4, BigDecimal.ZERO);
			int rowsUpdated = ps.executeUpdate();
			if (rowsUpdated != 1) {
				throw new DataAccessException("Failed to insert customer");
			}
		}
	}
	
	private int generateId(Connection conn) throws SQLException {
		try (PreparedStatement newIdStatement = conn.prepareStatement(GENERATE_CLIENT_ID);
				ResultSet rs = newIdStatement.executeQuery()) {
			if (!rs.next()) {
				throw new DataAccessException("Failed to generate new client id");
			}
			return rs.getInt(1);
		}
	}
}
