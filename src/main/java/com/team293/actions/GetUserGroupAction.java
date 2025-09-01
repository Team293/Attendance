package com.team293.actions;

import com.slack.api.methods.request.usergroups.UsergroupsListRequest;
import com.team293.Main;
import com.team293.actions.dto.UserGroupInfoRes;
import com.team293.config.AppConfig;
import com.team293.util.action.Action;
import com.team293.util.action.ActionParameter;
import com.team293.util.action.ActionResponse;

import java.util.List;

public class GetUserGroupAction implements Action<UserGroupInfoRes> {
    @Override
    public ActionResponse<UserGroupInfoRes> execute(List<ActionParameter<?>> parameters) throws Exception {
        List<AppConfig.Groups> allGroups = Main.config.groups();

        String userId = getParameterValue(parameters, "userId", String.class);

        String highestPriorityGroup = null;

        int highestPriority = Integer.MAX_VALUE;

        var slackUserGroups = Main.slack.methods(Main.token)
                .usergroupsList(
                         UsergroupsListRequest.builder()
                                 .includeUsers(true)
                                 .build()
                );

        if (!slackUserGroups.isOk() || slackUserGroups.getUsergroups() == null) {
            System.out.println(slackUserGroups);
            throw new IllegalStateException("Failed to fetch user groups from Slack.\n" + slackUserGroups.getError());
        }

        var userGroups = slackUserGroups.getUsergroups();

        for (var group : userGroups) {
            if (group.getUsers() != null && group.getUsers().contains(userId)) {
                for (var configGroup : allGroups) {
                    if (configGroup.slackId().equals(group.getId())) {
                        if (configGroup.priority() < highestPriority) {
                            highestPriority = configGroup.priority();
                            highestPriorityGroup = group.getId();
                        }
                    }
                }
            }
        }

        String finalHighestPriorityGroup = highestPriorityGroup;

        var slackUserGroup = userGroups.stream()
                .filter(g -> g.getId().equals(finalHighestPriorityGroup))
                .findFirst()
                .orElse(null);

        int maxMembers = slackUserGroup != null ? slackUserGroup.getUsers().size() : 0;

        return ActionResponse.success(new UserGroupInfoRes(
                highestPriority,
                highestPriorityGroup,
                maxMembers
        ));
    }

    @Override
    public List<ActionParameter<?>> getParameters() {
        return List.of(
                createParameter("userId", String.class)
        );
    }

    @Override
    public String actionId() {
        return "get_user_group";
    }
}
