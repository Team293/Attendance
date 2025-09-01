package com.team293.actions;

import com.team293.Main;
import com.team293.entities.AttendanceEntry;
import com.team293.util.action.Action;
import com.team293.util.action.ActionParameter;
import com.team293.util.action.ActionResponse;
import kotlin.Pair;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CreateAttendanceListAction implements Action<List<Pair<String, Integer>>> {
    @Override
    public ActionResponse<List<Pair<String, Integer>>> execute(List<ActionParameter<?>> parameters) throws Exception {
        List<Pair<String, Integer>> attendanceHourList = new ArrayList<>();

        Action<Integer> calculateHoursForUserAction = Main.getAction("calculate_hours_for_user");

        List<AttendanceEntry> allEntries = AttendanceEntry.find.all();

        for (AttendanceEntry entry : allEntries) {
            String userId = entry.getUserId();

            if (userId == null || userId.isEmpty()) {
                continue;
            }

            if (attendanceHourList.stream().anyMatch(pair -> pair.getFirst().equals(userId))) {
                continue;
            }

            ActionResponse<Integer> actionResponse = calculateHoursForUserAction.executeSafe(
                    List.of(
                            Action.createParameter("userId", String.class, userId),
                            Action.createParameter("startDate", LocalDateTime.class, null),
                            Action.createParameter("endDate", LocalDateTime.class, null),
                            Action.createParameter("allTime", Boolean.class, true)
                    )
            );

            if (actionResponse.isSuccess()) {
                int hours = actionResponse.getData();
                attendanceHourList.add(new Pair<>(userId, hours));
            } else {
                throw new RuntimeException("Failed to calculate hours for user: " + userId);
            }
        }

        return ActionResponse.success(attendanceHourList);
    }

    @Override
    public List<ActionParameter<?>> getParameters() {
        return List.of();
    }

    @Override
    public String actionId() {
        return "create_attendance_list";
    }
}
