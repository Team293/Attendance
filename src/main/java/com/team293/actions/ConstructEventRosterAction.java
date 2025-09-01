package com.team293.actions;

import com.slack.api.model.User;
import com.team293.Main;
import com.team293.actions.dto.UserGroupInfoRes;
import com.team293.config.AppConfig;
import com.team293.entities.TimedEvent;
import com.team293.util.action.Action;
import com.team293.util.action.ActionParameter;
import com.team293.util.action.ActionResponse;
import kotlin.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConstructEventRosterAction implements Action<String> {

    private static final String UNGROUPED = "ungrouped";

    @Override
    public ActionResponse<String> execute(List<ActionParameter<?>> parameters) throws Exception {
        long eventId = getParameterValue(parameters, "eventId", Long.class);
        var event = TimedEvent.find.byId(eventId);

        if (event == null) {
            return ActionResponse.failure("Event not found");
        }

        List<User> attendees = fetchAttendees(event);

        StringBuilder roster = new StringBuilder("*Event Roster for " + event.getName() + "*");
        if (event.getCapacity() != null && event.getCapacity() > 0) {
            roster.append(" (").append(attendees.size()).append("/").append(event.getCapacity()).append(")");
        }
        roster.append("\n");
        if (attendees.isEmpty()) {
            roster.append("_No attendees have checked in yet._");
            return ActionResponse.success(roster.toString());
        }

        Map<String, Pair<Integer, List<User>>> groupMap = new HashMap<>();
        Map<String, Integer> groupMemberCount = new HashMap<>();
        Map<String, List<User>> groupAllMembers = new HashMap<>();

        fillPresentGroups(attendees, groupMap, groupMemberCount);
        fetchAllMembersForGroups(groupMap, groupAllMembers, groupMemberCount);

        List<Map.Entry<String, Pair<Integer, List<User>>>> sortedGroups = sortGroupsByPriorityDesc(groupMap);

        for (Map.Entry<String, Pair<Integer, List<User>>> entry : sortedGroups) {
            String groupId = entry.getKey();
            List<User> presentUsers = entry.getValue().getSecond();
            appendGroupHeader(roster, groupId, presentUsers, groupMemberCount);
            appendPresentUsers(roster, presentUsers);
            appendAbsenteesIfKnown(roster, groupId, presentUsers, groupAllMembers);
        }

        return ActionResponse.success(roster.toString());
    }

    private List<User> fetchAttendees(TimedEvent event) throws Exception {
        List<User> attendees = new ArrayList<>();
        for (String attendeeId : event.getCheckedInUsers()) {
            var userInfo = Main.app.client().usersInfo(r -> r.user(attendeeId));
            if (userInfo.isOk() && userInfo.getUser() != null) {
                attendees.add(userInfo.getUser());
            }
        }
        return attendees;
    }

    private void fillPresentGroups(
            List<User> attendees,
            Map<String, Pair<Integer, List<User>>> groupMap,
            Map<String, Integer> groupMemberCount
    ) {
        for (User u : attendees) {
            Action<UserGroupInfoRes> getUserGroupAction = Main.getAction("get_user_group");
            var groupResponse = getUserGroupAction.executeSafe(List.of(
                    Action.createParameter("userId", String.class, u.getId())
            ));

            String groupId = (groupResponse.isSuccess() && groupResponse.getData() != null)
                    ? groupResponse.getData().slackId()
                    : UNGROUPED;

            int priority = resolvePriority(groupId);

            if (!UNGROUPED.equals(groupId) && groupResponse.getData() != null) {
                groupMemberCount.put(groupId, groupResponse.getData().maxMembers());
            }

            groupMap
                    .computeIfAbsent(groupId, k -> new Pair<>(priority, new ArrayList<>()))
                    .getSecond()
                    .add(u);
        }
    }

    private int resolvePriority(String groupId) {
        if (UNGROUPED.equals(groupId)) return -1;
        var groupConfig = Main.config.groups().stream()
                .filter(g -> g.slackId().equals(groupId))
                .findFirst();
        return groupConfig.map(AppConfig.Groups::priority).orElse(-1);
    }

    private void fetchAllMembersForGroups(
            Map<String, Pair<Integer, List<User>>> groupMap,
            Map<String, List<User>> groupAllMembers,
            Map<String, Integer> groupMemberCount
    ) throws Exception {
        for (String groupId : new ArrayList<>(groupMap.keySet())) {
            if (UNGROUPED.equals(groupId)) continue;
            if (groupAllMembers.containsKey(groupId)) continue;

            var ugResp = Main.app.client().usergroupsUsersList(r -> r.usergroup(groupId));
            if (ugResp != null && ugResp.isOk() && ugResp.getUsers() != null) {
                List<User> members = new ArrayList<>();
                for (String uid : ugResp.getUsers()) {
                    var ui = Main.app.client().usersInfo(r -> r.user(uid));
                    if (ui.isOk() && ui.getUser() != null) {
                        members.add(ui.getUser());
                    }
                }
                groupAllMembers.put(groupId, members);
                groupMemberCount.putIfAbsent(groupId, members.size());
            } else {
                groupAllMembers.put(groupId, new ArrayList<>());
            }
        }
    }

    private List<Map.Entry<String, Pair<Integer, List<User>>>> sortGroupsByPriorityDesc(
            Map<String, Pair<Integer, List<User>>> groupMap
    ) {
        List<Map.Entry<String, Pair<Integer, List<User>>>> sorted = new ArrayList<>(groupMap.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().getFirst(), a.getValue().getFirst()));
        return sorted;
    }

    private void appendGroupHeader(
            StringBuilder roster,
            String groupId,
            List<User> presentUsers,
            Map<String, Integer> groupMemberCount
    ) {
        roster.append("\n*Group: ");
        if (!UNGROUPED.equals(groupId)) {
            roster.append("<!subteam^").append(groupId).append(">");
            roster.append(" (").append(presentUsers.size()).append("/")
                    .append(groupMemberCount.getOrDefault(groupId, 0)).append(")");
        } else {
            roster.append("Ungrouped");
        }
        roster.append("*\n");
    }

    private void appendPresentUsers(StringBuilder roster, List<User> presentUsers) {
        for (User u : presentUsers) {
            roster.append("- ").append(displayName(u)).append("\n");
        }
    }

    private void appendAbsenteesIfKnown(
            StringBuilder roster,
            String groupId,
            List<User> presentUsers,
            Map<String, List<User>> groupAllMembers
    ) {
        if (UNGROUPED.equals(groupId)) return;

        List<User> allMembers = groupAllMembers.getOrDefault(groupId, List.of());
        if (allMembers.isEmpty()) {
            roster.append("_Absent: unknown (couldn't fetch user group members)_\n");
            return;
        }

        Set<String> presentIds = new HashSet<>();
        for (User u : presentUsers) presentIds.add(u.getId());

        List<User> absentees = new ArrayList<>();
        for (User m : allMembers) {
            if (!presentIds.contains(m.getId())) {
                absentees.add(m);
            }
        }

        if (!absentees.isEmpty()) {
            roster.append("_Absent:_ ");
            for (int i = 0; i < absentees.size(); i++) {
                roster.append("_").append(displayName(absentees.get(i))).append("_");
                if (i < absentees.size() - 1) roster.append(", ");
            }
            roster.append("\n");
        }
    }

    private String displayName(User u) {
        return u.getRealName() != null ? u.getRealName() : u.getName();
    }

    @Override
    public List<ActionParameter<?>> getParameters() {
        return List.of(createParameter("eventId", Long.class));
    }

    @Override
    public String actionId() {
        return "construct_event_roster";
    }
}