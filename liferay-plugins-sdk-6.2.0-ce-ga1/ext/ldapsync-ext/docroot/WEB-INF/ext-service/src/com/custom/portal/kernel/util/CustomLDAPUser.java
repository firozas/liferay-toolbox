/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.custom.portal.kernel.util;

import com.liferay.portal.model.Address;
import com.liferay.portal.model.Phone;
import com.liferay.portal.security.ldap.LDAPUser;

public class CustomLDAPUser extends LDAPUser {
	
	public String getPhone() {
		return _phone.getNumber();
	}
	
	public void setPhone(Phone phone) {
		_phone = phone;
	}

	public Address getAddress() {
		return _address;
	}
	
	public void setAddress(Address address) {
		_address = address;
	}
	
	public String getCity() {
		return _address.getCity();
	}
	
	public String getZip() {
		return _address.getZip();
	}
	
	public String getStreet() {
		return _address.getStreet1();
	}

	private Phone _phone;
	private Address _address;
}