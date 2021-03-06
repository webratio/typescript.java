/**
 *  Copyright (c) 2015-2017 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 *  
 */
package ts.eclipse.ide.terminal.interpreter.internal;

import java.util.Map;

import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;

import ts.eclipse.ide.terminal.interpreter.LineCommand;

public interface ITerminalConnectorWrapper extends ITerminalConnector {

	void executeCommand(LineCommand cmd, Map<String, Object> properties);

	boolean hasWorkingDirChanged(String workingDir);

	String getWorkingDir();

}
