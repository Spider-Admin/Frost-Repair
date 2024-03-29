/*
  Copyright 2021 - 2022 Spider-Admin@Z+d9Knmjd3hQeeZU6BOWPpAAxxs

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package org.frost.repair;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

	private static final Logger log = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {

		// Ctrl+C from Gradle does not always call the ShutdownHook
		// Gradle uses Process.destroy(), which is platform-dependent:
		// - Windows platforms only support a forcible kill signal.
		// - Linux platforms support a normal (non-forcible) kill signal.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				log.info("Frost-Repair finished.");
			}
		});

		try {
			if (args.length >= 1) {
				String frostPath = args[0];
				FrostRepair frostRepair = new FrostRepair();
				frostRepair.repair(frostPath);
			} else {
				System.out.println("Parameter 1 \"path to Frost\" is missing!");
			}
		} catch (IOException e) {
			log.error("IO-Error!", e);
		}
	}
}