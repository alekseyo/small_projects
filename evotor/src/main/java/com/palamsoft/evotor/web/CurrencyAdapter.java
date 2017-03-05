package com.palamsoft.evotor.web;

import java.math.BigDecimal;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class CurrencyAdapter extends XmlAdapter<String, BigDecimal> {

	@Override
	public BigDecimal unmarshal(String v) throws Exception {
		return new BigDecimal(v);
	}

	@Override
	public String marshal(BigDecimal v) throws Exception {
		if (v == null) {
			return null;
		}
		return v.setScale(2, BigDecimal.ROUND_HALF_UP).toString();
	}


}
