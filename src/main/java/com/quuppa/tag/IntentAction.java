package com.quuppa.tag;

public enum IntentAction {
	QT_SYSTEM_ERROR, QT_SYSTEM_EVENT, QT_BLE_NOT_ENABLED, QT_MOVING, QT_STATIONARY, QT_STARTED, QT_STOPPED, QT_STATIONARY_CHECK;
	
	public String fqdn() {
		return IntentAction.class.getName() + "." + name();
	}
}