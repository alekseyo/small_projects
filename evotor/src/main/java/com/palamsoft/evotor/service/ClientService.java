package com.palamsoft.evotor.service;

import java.math.BigDecimal;

import com.palamsoft.evotor.model.Client;

public interface ClientService {
	void addClient(Client client);
	BigDecimal getBalance(String clientLogin, String password);

}
