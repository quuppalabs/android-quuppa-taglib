// Copyright 2025 Quuppa Oy
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//    http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.quuppa.tag;

public enum IntentAction {
	QT_SYSTEM_ERROR, QT_SYSTEM_EVENT, QT_SCHEDULE_NOT_ENABLED, QT_BLE_NOT_ENABLED, QT_MOVING, QT_STATIONARY, QT_STARTED, QT_STOPPED, QT_STATIONARY_CHECK, QT_RESTART;
	
	public String fullyQualifiedName() {
		return IntentAction.class.getName() + "." + name();
	}
	
	public static IntentAction fullyQualifiedValueOf(String value) {
		if (!value.startsWith(IntentAction.class.getName() + ".") ) {
			throw new IllegalArgumentException("Value is not fully qualified with " + IntentAction.class.getName());
		}
		value = value.substring(IntentAction.class.getName().length() + 1);
		return IntentAction.valueOf(value);
	}
}