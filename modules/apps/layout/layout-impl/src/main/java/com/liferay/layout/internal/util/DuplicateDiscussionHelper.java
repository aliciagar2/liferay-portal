/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
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

package com.liferay.layout.internal.util;

import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.message.boards.model.MBMessage;
import com.liferay.message.boards.service.MBMessageLocalService;
import com.liferay.portal.kernel.comment.CommentManager;
import com.liferay.portal.kernel.comment.Discussion;
import com.liferay.portal.kernel.comment.DiscussionComment;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.util.List;
import java.util.function.Function;

/**
 * @author Alicia García García
 */
public class DuplicateDiscussionHelper {

	public void duplicateDisscussion(
			long oldFragmentEntryLinkId, long newFragmentEntryLinkId)
		throws PortalException {

		if (!_commentManager.hasDiscussion(
				FragmentEntryLink.class.getName(), oldFragmentEntryLinkId)) {

			return;
		}

		Discussion discussion = _commentManager.getDiscussion(
			_targetLayout.getUserId(), _targetLayout.getGroupId(),
			FragmentEntryLink.class.getName(), oldFragmentEntryLinkId,
			createServiceContextFunction(_serviceContext));

		DiscussionComment rootDiscussionComment =
			discussion.getRootDiscussionComment();

		MBMessage rootMBMessage = _mbMessageLocalService.addDiscussionMessage(
			rootDiscussionComment.getUserId(),
			rootDiscussionComment.getUserName(),
			rootDiscussionComment.getGroupId(),
			rootDiscussionComment.getClassName(), newFragmentEntryLinkId,
			WorkflowConstants.ACTION_PUBLISH);

		rootMBMessage.setCreateDate(rootDiscussionComment.getCreateDate());
		rootMBMessage.setModifiedDate(rootDiscussionComment.getModifiedDate());

		_mbMessageLocalService.updateMBMessage(rootMBMessage);

		_commentManager.subscribeDiscussion(
			_targetLayout.getUserId(), _targetLayout.getGroupId(),
			FragmentEntryLink.class.getName(), newFragmentEntryLinkId);

		if (rootDiscussionComment.getDescendantCommentsCount() <= 0) {
			return;
		}

		List<DiscussionComment> parentComments =
			rootDiscussionComment.getDescendantComments();

		for (DiscussionComment parentComment : parentComments) {
			MBMessage parent = _mbMessageLocalService.getMBMessage(
				parentComment.getCommentId());

			MBMessage parentMessage =
				_mbMessageLocalService.addDiscussionMessage(
					parent.getUserId(), parent.getUserName(),
					parent.getGroupId(), parent.getClassName(),
					newFragmentEntryLinkId, rootMBMessage.getThreadId(),
					rootMBMessage.getMessageId(), String.valueOf(Math.random()),
					parent.getBody(), _serviceContext);

			if (parentComment.getDescendantCommentsCount() > 0) {
				List<DiscussionComment> childComments =
					parentComment.getDescendantComments();

				for (DiscussionComment child : childComments) {
					MBMessage mbChildMessage =
						_mbMessageLocalService.addDiscussionMessage(
							child.getUserId(), child.getUserName(),
							child.getGroupId(), child.getClassName(),
							newFragmentEntryLinkId, parentMessage.getThreadId(),
							parentMessage.getMessageId(),
							String.valueOf(Math.random()), child.getBody(),
							_serviceContext);

					mbChildMessage.setCreateDate(child.getCreateDate());
					mbChildMessage.setModifiedDate(child.getModifiedDate());

					_mbMessageLocalService.updateMBMessage(mbChildMessage);
				}
			}

			parentMessage.setCreateDate(parent.getCreateDate());
			parentMessage.setModifiedDate(parent.getModifiedDate());
			parentMessage.setStatus(parent.getStatus());

			_mbMessageLocalService.updateMBMessage(parentMessage);
		}
	}

	protected DuplicateDiscussionHelper(
		CommentManager commentManager,
		MBMessageLocalService mbMessageLocalService, Layout targetLayout,
		ServiceContext serviceContext) {

		_commentManager = commentManager;
		_mbMessageLocalService = mbMessageLocalService;
		_targetLayout = targetLayout;
		_serviceContext = serviceContext;
	}

	protected Function<String, ServiceContext> createServiceContextFunction(
		ServiceContext serviceContext) {

		return className -> serviceContext;
	}

	private final CommentManager _commentManager;
	private final MBMessageLocalService _mbMessageLocalService;
	private final ServiceContext _serviceContext;
	private final Layout _targetLayout;

}