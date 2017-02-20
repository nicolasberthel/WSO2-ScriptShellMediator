package org.wso2.custom.mediator.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Class executing script shell commands
 */
public class ShellExecutor {
	private String command;

	/**
	 * Constructor of unix command executor
	 * @param cmd string containing unix bash commands
	 */
	public ShellExecutor(String cmd) {
		this.command = cmd;
	}

	/**
	 * Executed command that was passed at the constructor
	 * @return stdout of executed command.
	 */
	public String execute() {
		StringBuilder output = new StringBuilder();
		Process p;
		try {
			p = Runtime.getRuntime().exec(command);
			p.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					p.getInputStream()));

			String line = "";
			while ((line = reader.readLine()) != null) {
				output.append(line + "\t");
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return output.toString();
	}
}
