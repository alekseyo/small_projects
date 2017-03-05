package com.palamsoft.evotor.model;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.eclipse.persistence.oxm.annotations.XmlPath;

import com.palamsoft.evotor.web.CurrencyAdapter;

@XmlRootElement(name="response")
@XmlType(propOrder={"resultCode", "balance"})
@XmlAccessorType(XmlAccessType.FIELD)
public class ClientBalanceResponse {

	@XmlElement(name="result-code")
	private Integer resultCode;
	
	@XmlPath("extra[@name='balance']/text()")
	@XmlJavaTypeAdapter(CurrencyAdapter.class)
	private BigDecimal balance;

	public Integer getResultCode() {
		return resultCode;
	}

	public void setResultCode(Integer resultCode) {
		this.resultCode = resultCode;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

}
