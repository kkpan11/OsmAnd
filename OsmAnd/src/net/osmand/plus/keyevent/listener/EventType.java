package net.osmand.plus.keyevent.listener;

import net.osmand.util.CollectionUtils;

public enum EventType {

	SELECT_DEVICE,
	RENAME_DEVICE,
	ADD_NEW_DEVICE,
	DELETE_DEVICE,

	RENAME_ASSIGNMENT,
	UPDATE_ASSIGNMENT_KEYCODE,
	ADD_ASSIGNMENT_KEYCODE,
	REMOVE_ASSIGNMENT_KEYCODE,
	SAVE_UPDATED_ASSIGNMENTS_LIST,
	REMOVE_KEY_ASSIGNMENT_COMPLETELY,
	CLEAR_ALL_ASSIGNMENTS;

	public boolean isDeviceRelated() {
		return CollectionUtils.equalsToAny(this, SELECT_DEVICE,
				RENAME_DEVICE, ADD_NEW_DEVICE, DELETE_DEVICE);
	}

	public boolean isAssignmentRelated() {
		return CollectionUtils.equalsToAny(this, RENAME_ASSIGNMENT,
				UPDATE_ASSIGNMENT_KEYCODE, ADD_ASSIGNMENT_KEYCODE,
				REMOVE_ASSIGNMENT_KEYCODE, CLEAR_ALL_ASSIGNMENTS,
				SAVE_UPDATED_ASSIGNMENTS_LIST, REMOVE_KEY_ASSIGNMENT_COMPLETELY);
	}

	public boolean isCustomPreferenceRelated() {
		return this != SELECT_DEVICE;
	}
}
