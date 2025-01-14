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

/*
 * QuuppaTag specific exceptions 
 */
public class QuuppaTagException extends Exception {
	private static final long serialVersionUID = 7615571782054534590L;
	
	public QuuppaTagException(String message) {
		super(message);
	}
	
	public QuuppaTagException(String message, Exception e) {
		super(message, e);
	}
	
}
