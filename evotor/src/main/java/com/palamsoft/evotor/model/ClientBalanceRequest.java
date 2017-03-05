package com.palamsoft.evotor.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.eclipse.persistence.oxm.annotations.XmlPath;

@XmlRootElement(name="request")
@XmlType(propOrder={"requestType", "login", "password"})
@XmlAccessorType(XmlAccessType.FIELD)
public class ClientBalanceRequest {

	@XmlElement(name="request-type")
	private String requestType;
	
	@XmlPath("extra[@name='login']/text()")
	private String login;
	
	@XmlPath("extra[@name='password']/text()")
	private String password;

	public String getRequestType() {
		return requestType;
	}

	public void setRequestType(String requestType) {
		this.requestType = requestType;
	}

	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		this.login = login;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	
}
