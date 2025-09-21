package com.team293.actions;

import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.model.User;
import com.team293.Main;
import com.team293.util.action.Action;
import com.team293.util.action.ActionParameter;
import com.team293.util.action.ActionResponse;

import java.util.List;

public class GetUserIdFromHandleAction implements Action<String> {
    @Override
    public ActionResponse<String> execute(List<ActionParameter<?>> parameters) throws Exception {
        String userHandle = getParameterValue(parameters, "userHandle", String.class).replace("@", "");

        String userId = null;

        try {
            UsersListResponse usersListResponse = Main.slack.methods().usersList(r -> r.token(Main.token));
            if (usersListResponse.isOk() && usersListResponse.getMembers() != null) {
                for (User user : usersListResponse.getMembers()) {
                    if (userHandle.equals(user.getName())) {
                        userId = user.getId();
                        break;
                    }
                }
            }
            if (userId == null) {
                throw new IllegalArgumentException("User handle not found: @" + userHandle);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve user handle to ID", e);
        }

        return ActionResponse.success(userId);
    }

    @Override
    public List<ActionParameter<?>> getParameters() {
        return List.of(
                createParameter("userHandle", String.class)
        );
    }

    @Override
    public String actionId() {
        return "get_user_id_from_handle";
    }
}
