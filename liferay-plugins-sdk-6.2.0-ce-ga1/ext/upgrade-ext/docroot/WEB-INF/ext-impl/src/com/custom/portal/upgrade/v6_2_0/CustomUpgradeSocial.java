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

package com.custom.portal.upgrade.v6_2_0;

import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.upgrade.v6_2_0.UpgradeSocial;
import com.liferay.portlet.social.model.SocialActivity;
import com.liferay.portlet.social.service.SocialActivityLocalServiceUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;

/**
 * @author Sergio Sanchez
 * @author Zsolt Berentey
 */
public class CustomUpgradeSocial extends UpgradeSocial {
	
	protected void addActivity(
			long activityId, long groupId, long companyId, long userId,
			Timestamp createDate, long mirrorActivityId, long classNameId,
			long classPK, int type, String extraData, long receiverUserId)
		throws Exception {

		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		
		boolean isCreateDateUnique = false;
		long updatedCreateDate = createDate.getTime();
		
		isCreateDateUnique = checkCreateDate(updatedCreateDate, classNameId, classPK);
		
		while (!isCreateDateUnique)
		{
			updatedCreateDate += 1;
			isCreateDateUnique = checkCreateDate(updatedCreateDate, classNameId, classPK);
		}

		try {
			con = DataAccess.getUpgradeOptimizedConnection();

			StringBundler sb = new StringBundler(5);

			sb.append("insert into SocialActivity (activityId, groupId, ");
			sb.append("companyId, userId, createDate, mirrorActivityId, ");
			sb.append("classNameId, classPK, type_, extraData, ");
			sb.append("receiverUserId) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ");
			sb.append("?)");

			ps = con.prepareStatement(sb.toString());

			ps.setLong(1, activityId);
			ps.setLong(2, groupId);
			ps.setLong(3, companyId);
			ps.setLong(4, userId);
			ps.setLong(5, updatedCreateDate);
			ps.setLong(6, mirrorActivityId);
			ps.setLong(7, classNameId);
			ps.setLong(8, classPK);
			ps.setInt(9, type);
			ps.setString(10, extraData);
			ps.setLong(11, receiverUserId);

			ps.executeUpdate();
		}
		catch (Exception e) {
			if (_log.isWarnEnabled()) {
				_log.warn("Unable to add activity " + activityId, e);
			}
		}
		finally {
			DataAccess.cleanUp(con, ps, rs);
		}
	}
	
	private boolean checkCreateDate(long updatedCreateDate, long classNameId,
			long classPK) throws SystemException {
		
		List<SocialActivity> activities = SocialActivityLocalServiceUtil.getActivities(0, classNameId, classPK, 
				QueryUtil.ALL_POS, QueryUtil.ALL_POS);
	
		for (SocialActivity activity : activities) {
			if (updatedCreateDate == activity.getCreateDate()) {
				return false;
			}
		}
		return true;
	}
	
	private static Log _log = LogFactoryUtil.getLog(CustomUpgradeSocial.class);
}