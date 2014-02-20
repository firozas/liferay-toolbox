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

import java.util.Properties;

import javax.naming.directory.Attributes;

import com.liferay.portal.security.ldap.LDAPToPortalConverter;

/**
 * @author Edward Han
 * @author Brian Wing Shun Chan
 */
public interface CustomLDAPToPortalConverter extends LDAPToPortalConverter{

	public CustomLDAPUser importLDAPUser(
			long companyId, Attributes attributes, Properties userMappings,
			Properties userExpandoMappings, Properties contactMappings,
			Properties contactExpandoMappings, String password)
		throws Exception;
}