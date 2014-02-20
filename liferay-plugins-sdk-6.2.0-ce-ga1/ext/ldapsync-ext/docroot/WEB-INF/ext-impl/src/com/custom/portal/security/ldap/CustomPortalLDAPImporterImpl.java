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

package com.custom.portal.security.ldap;

import com.custom.portal.kernel.util.CustomLDAPToPortalConverter;
import com.custom.portal.kernel.util.CustomLDAPUser;
import com.liferay.portal.NoSuchUserGroupException;
import com.liferay.portal.kernel.ldap.LDAPUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.security.pacl.DoPrivileged;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.CalendarFactoryUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Address;
import com.liferay.portal.model.Contact;
import com.liferay.portal.model.Phone;
import com.liferay.portal.model.User;
import com.liferay.portal.model.UserGroup;
import com.liferay.portal.security.auth.ScreenNameGenerator;
import com.liferay.portal.security.auth.ScreenNameGeneratorFactory;
import com.liferay.portal.security.ldap.AttributesTransformer;
import com.liferay.portal.security.ldap.AttributesTransformerFactory;
import com.liferay.portal.security.ldap.LDAPGroup;
import com.liferay.portal.security.ldap.LDAPUserGroupTransactionThreadLocal;
import com.liferay.portal.security.ldap.LDAPUserTransactionThreadLocal;
import com.liferay.portal.security.ldap.PortalLDAPImporterImpl;
import com.liferay.portal.service.AddressLocalServiceUtil;
import com.liferay.portal.service.PhoneLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserGroupLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PrefsPropsUtil;
import com.liferay.portal.util.PropsValues;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import javax.naming.directory.Attributes;

/**
 * @author Michael C. Han
 * @author Brian Wing Shun Chan
 * @author Wesley Gong
 * @author Hugo Huijser
 */
@DoPrivileged
public class CustomPortalLDAPImporterImpl extends PortalLDAPImporterImpl {

	public void setLDAPToPortalConverter(
			CustomLDAPToPortalConverter ldapToPortalConverter) {

		_ldapToPortalConverter = ldapToPortalConverter;
	}

	@Override
	protected User importUser(
			long ldapServerId, long companyId, Attributes attributes,
			Properties userMappings, Properties userExpandoMappings,
			Properties contactMappings, Properties contactExpandoMappings,
			String password)
		throws Exception {
		
		LDAPUserTransactionThreadLocal.setOriginatesFromLDAP(true);

		try {
			AttributesTransformer attributesTransformer =
				AttributesTransformerFactory.getInstance();

			attributes = attributesTransformer.transformUser(attributes);

			CustomLDAPUser ldapUser = _ldapToPortalConverter.importLDAPUser(
				companyId, attributes, userMappings, userExpandoMappings,
				contactMappings, contactExpandoMappings, password);

			User user = getUser(companyId, ldapUser);

			if ((user != null) && user.isDefaultUser()) {
				return user;
			}

			ServiceContext serviceContext = ldapUser.getServiceContext();

			serviceContext.setAttribute("ldapServerId", ldapServerId);

			boolean isNew = false;

			if (user == null) {
				user = addUser(companyId, ldapUser, password);

				isNew = true;
			}

			String modifiedDate = LDAPUtil.getAttributeString(
				attributes, "modifyTimestamp");

			user = updateUser(
				companyId, ldapUser, user, userMappings, contactMappings,
				password, modifiedDate, isNew);

			updateExpandoAttributes(
				user, ldapUser, userExpandoMappings, contactExpandoMappings);

			return user;
		}
		finally {
			LDAPUserTransactionThreadLocal.setOriginatesFromLDAP(false);
		}
	}

	@Override
	protected UserGroup importUserGroup(
			long companyId, Attributes attributes, Properties groupMappings)
		throws Exception {

		AttributesTransformer attributesTransformer =
			AttributesTransformerFactory.getInstance();

		attributes = attributesTransformer.transformGroup(attributes);

		LDAPGroup ldapGroup = _ldapToPortalConverter.importLDAPGroup(
			companyId, attributes, groupMappings);

		UserGroup userGroup = null;

		try {
			userGroup = UserGroupLocalServiceUtil.getUserGroup(
				companyId, ldapGroup.getGroupName());

			if (!Validator.equals(
					userGroup.getDescription(), ldapGroup.getDescription())) {

				UserGroupLocalServiceUtil.updateUserGroup(
					companyId, userGroup.getUserGroupId(),
					ldapGroup.getGroupName(), ldapGroup.getDescription(), null);
			}
		}
		catch (NoSuchUserGroupException nsuge) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Adding user group to portal " + ldapGroup.getGroupName());
			}

			long defaultUserId = UserLocalServiceUtil.getDefaultUserId(
				companyId);

			LDAPUserGroupTransactionThreadLocal.setOriginatesFromLDAP(true);

			try {
				userGroup = UserGroupLocalServiceUtil.addUserGroup(
					defaultUserId, companyId, ldapGroup.getGroupName(),
					ldapGroup.getDescription(), null);
			}
			catch (Exception e) {
				if (_log.isWarnEnabled()) {
					_log.warn(
						"Unable to create user group " +
							ldapGroup.getGroupName());
				}

				if (_log.isDebugEnabled()) {
					_log.debug(e, e);
				}
			}
			finally {
				LDAPUserGroupTransactionThreadLocal.setOriginatesFromLDAP(
					false);
			}
		}

		addRole(companyId, ldapGroup, userGroup);

		return userGroup;
	}
	
	protected User updateUser(
			long companyId, CustomLDAPUser ldapUser, User user,
			Properties userMappings, Properties contactMappings,
			String password, String modifiedDate, boolean isNew)
		throws Exception {

		Date ldapUserModifiedDate = null;

		boolean passwordReset = ldapUser.isPasswordReset();

		if (PrefsPropsUtil.getBoolean(
				companyId, PropsKeys.LDAP_EXPORT_ENABLED,
				PropsValues.LDAP_EXPORT_ENABLED)) {

			passwordReset = user.isPasswordReset();
		}

		try {
			if (Validator.isNotNull(modifiedDate)) {
				ldapUserModifiedDate = LDAPUtil.parseDate(modifiedDate);

				if (ldapUserModifiedDate.equals(user.getModifiedDate())) {
					if (ldapUser.isAutoPassword()) {
						if (_log.isDebugEnabled()) {
							_log.debug(
								"Skipping user " + user.getEmailAddress() +
									" because he is already synchronized");
						}

						return user;
					}

					UserLocalServiceUtil.updatePassword(
						user.getUserId(), password, password, passwordReset,
						true);

					if (_log.isDebugEnabled()) {
						_log.debug(
							"User " + user.getEmailAddress() +
								" is already synchronized, but updated " +
									"password to avoid a blank value");
					}

					return user;
				}
			}
			else if (!isNew) {
				if (_log.isInfoEnabled()) {
					_log.info(
						"Skipping user " + user.getEmailAddress() +
							" because the LDAP entry was never modified");
				}

				return user;
			}
		}
		catch (ParseException pe) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to parse LDAP modify timestamp " + modifiedDate,
					pe);
			}
		}

		if (!PropsValues.LDAP_IMPORT_USER_PASSWORD_ENABLED) {
			password = PropsValues.LDAP_IMPORT_USER_PASSWORD_DEFAULT;

			if (StringUtil.equalsIgnoreCase(
					password, _USER_PASSWORD_SCREEN_NAME)) {

				password = ldapUser.getScreenName();
			}
		}

		if (Validator.isNull(ldapUser.getScreenName())) {
			ldapUser.setAutoScreenName(true);
		}

		if (ldapUser.isAutoScreenName()) {
			ScreenNameGenerator screenNameGenerator =
				ScreenNameGeneratorFactory.getInstance();

			ldapUser.setScreenName(
				screenNameGenerator.generate(
					companyId, user.getUserId(), ldapUser.getEmailAddress()));
		}

		Calendar birthdayCal = CalendarFactoryUtil.getCalendar();

		Contact ldapContact = ldapUser.getContact();

		birthdayCal.setTime(ldapContact.getBirthday());

		int birthdayMonth = birthdayCal.get(Calendar.MONTH);
		int birthdayDay = birthdayCal.get(Calendar.DAY_OF_MONTH);
		int birthdayYear = birthdayCal.get(Calendar.YEAR);

		if (ldapUser.isUpdatePassword()) {
			UserLocalServiceUtil.updatePassword(
				user.getUserId(), password, password, passwordReset, true);
		}

		updateLDAPUser(
			ldapUser.getUser(), ldapContact, user, userMappings,
			contactMappings);

		user = UserLocalServiceUtil.updateUser(
			user.getUserId(), password, StringPool.BLANK, StringPool.BLANK,
			passwordReset, ldapUser.getReminderQueryQuestion(),
			ldapUser.getReminderQueryAnswer(), ldapUser.getScreenName(),
			ldapUser.getEmailAddress(), ldapUser.getFacebookId(),
			ldapUser.getOpenId(), ldapUser.getLanguageId(),
			ldapUser.getTimeZoneId(), ldapUser.getGreeting(),
			ldapUser.getComments(), ldapUser.getFirstName(),
			ldapUser.getMiddleName(), ldapUser.getLastName(),
			ldapUser.getPrefixId(), ldapUser.getSuffixId(), ldapUser.isMale(),
			birthdayMonth, birthdayDay, birthdayYear, ldapUser.getSmsSn(),
			ldapUser.getAimSn(), ldapUser.getFacebookSn(), ldapUser.getIcqSn(),
			ldapUser.getJabberSn(), ldapUser.getMsnSn(),
			ldapUser.getMySpaceSn(), ldapUser.getSkypeSn(),
			ldapUser.getTwitterSn(), ldapUser.getYmSn(), ldapUser.getJobTitle(),
			ldapUser.getGroupIds(), ldapUser.getOrganizationIds(),
			ldapUser.getRoleIds(), ldapUser.getUserGroupRoles(),
			ldapUser.getUserGroupIds(), ldapUser.getServiceContext());
		
		boolean hasBusinessPhone = false;
		
		for (Phone phone : user.getPhones())
		{
			if (phone.getTypeId() == 11006) {
				hasBusinessPhone = true;
				break;
			}
		}
		
		if (!hasBusinessPhone && !ldapUser.getPhone().isEmpty()) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Adding Business phone: " + ldapUser.getPhone());
			}
			PhoneLocalServiceUtil.addPhone(user.getUserId(), Contact.class.getName(), user.getContactId(),
					ldapUser.getPhone(), "", 11006, true, ldapUser.getServiceContext());
		}
		
		boolean hasBusinessAddress = false;
		
		for (Address address : user.getAddresses())
		{
			if (address.getTypeId() == 11000) {
				hasBusinessAddress = true;
				break;
			}
		}
		
		if (!hasBusinessAddress && !ldapUser.getStreet().isEmpty() && !ldapUser.getCity().isEmpty()) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Adding Business Address: " + ldapUser.getStreet() + " " + ldapUser.getCity() +
						" " + ldapUser.getZip());

			}
			AddressLocalServiceUtil.addAddress(user.getUserId(), Contact.class.getName(), user.getContactId(), 
					ldapUser.getStreet(), "", "", ldapUser.getCity(), ldapUser.getZip(),
					0, 0, 11000, true, true, ldapUser.getServiceContext());
		}
	
		user = UserLocalServiceUtil.updateStatus(
			user.getUserId(), ldapUser.getStatus());
		
		if (ldapUserModifiedDate != null) {
			user = UserLocalServiceUtil.updateModifiedDate(
				user.getUserId(), ldapUserModifiedDate);
		}

		if (ldapUser.isUpdatePortrait()) {
			byte[] portraitBytes = ldapUser.getPortraitBytes();

			if (ArrayUtil.isNotEmpty(portraitBytes)) {
				UserLocalServiceUtil.updatePortrait(
					user.getUserId(), portraitBytes);
			}
			else {
				UserLocalServiceUtil.deletePortrait(user.getUserId());
			}
		}
		
		return user;
	}

	private static final String _USER_PASSWORD_SCREEN_NAME = "screenName";
	
	private static Log _log = LogFactoryUtil.getLog(
		CustomPortalLDAPImporterImpl.class);

	private CustomLDAPToPortalConverter _ldapToPortalConverter;
}