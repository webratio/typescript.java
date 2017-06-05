/**
 *  Copyright (c) 2013-2016 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package ts.eclipse.ide.internal.core.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;

import ts.cmd.tslint.TslintSettingsStrategy;
import ts.eclipse.ide.core.TypeScriptCorePlugin;
import ts.eclipse.ide.core.nodejs.IDENodejsProcessHelper;
import ts.eclipse.ide.core.nodejs.IEmbeddedNodejs;
import ts.eclipse.ide.core.nodejs.INodejsInstallManager;
import ts.eclipse.ide.core.preferences.TypeScriptCorePreferenceConstants;
import ts.eclipse.ide.core.resources.UseSalsa;
import ts.eclipse.ide.core.utils.PreferencesHelper;
import ts.eclipse.ide.internal.core.Trace;
import ts.repository.ITypeScriptRepository;

/**
 * Eclipse preference initializer for TypeScript Core.
 * 
 */
public class TypeScriptCorePreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		IEclipsePreferences node = PreferencesHelper
				.getWorkspaceDefaultPreferences(TypeScriptCorePlugin.PLUGIN_ID);

		// initialize properties for direct access of node.js server (start an
		// internal process)
		initializeNodejsPreferences(node);

		try {
			ITypeScriptRepository defaultRepository = TypeScriptCorePlugin.getTypeScriptRepositoryManager()
					.getDefaultRepository();
			if (defaultRepository == null) {
				Trace.trace(Trace.WARNING, "No default TypeScript repository is available");
			}

			// Initialize TypeScript runtime preferences
			initializeTypeScriptRuntimePreferences(node, defaultRepository);
			// Initialize tsserver preferences
			initializeTsserverPreferences(node, defaultRepository);
			// Initialize install @types preferences
			initializeInstallTypesPreferences(node);
			// Initialize tslint preferences
			initializeTslintPreferences(node, defaultRepository);
		} catch (Exception e) {
			Trace.trace(Trace.SEVERE, "Error while getting the default TypeScript repository", e);
		}

		// initialize Salsa (use TypeScript Language Service for JavaSCript
		// files)
		initializeSalsa(node);

		// Initialize default path where TypeScript files *.ts, *.tsx must be
		// searched for compilation and validation must be done
		initializeTypeScriptBuildPath(node);

		// initialize editor+formation options
		initializeEditorFormatOptions(node);

		// Fix embedded TypeScript id preference
		// See https://github.com/angelozerr/typescript.java/issues/121
		fixEmbeddedPreference(
				PreferencesHelper.getWorkspacePreferences(TypeScriptCorePlugin.PLUGIN_ID));
	}

	/**
	 * initialize properties for direct access of node.js server (start an
	 * internal process)
	 * 
	 * @param node
	 */
	private void initializeNodejsPreferences(IEclipsePreferences node) {
		// By default use the embedded Node.js install (if exists)
		if (!useBundledNodeJsEmbedded(node)) {
			// Use installed node.js in case there is no embedded install.
			node.putBoolean(TypeScriptCorePreferenceConstants.USE_NODEJS_EMBEDDED, false);
		} else {
			node.putBoolean(TypeScriptCorePreferenceConstants.USE_NODEJS_EMBEDDED, true);
		}
		node.put(TypeScriptCorePreferenceConstants.NODEJS_PATH, IDENodejsProcessHelper.getNodejsPath());
	}

	private static boolean useBundledNodeJsEmbedded(IEclipsePreferences node) {
		INodejsInstallManager installManager = TypeScriptCorePlugin.getNodejsInstallManager();
		IEmbeddedNodejs[] installs = installManager.getNodejsInstalls();
		for (IEmbeddedNodejs install : installs) {
			node.put(TypeScriptCorePreferenceConstants.NODEJS_EMBEDDED_ID, install.getId());
			return true;
		}
		return false;
	}

	private void initializeTypeScriptRuntimePreferences(IEclipsePreferences node,
			ITypeScriptRepository defaultRepository) {
		if (defaultRepository != null) {
			node.put(TypeScriptCorePreferenceConstants.EMBEDDED_TYPESCRIPT_ID, defaultRepository.getName());
			node.putBoolean(TypeScriptCorePreferenceConstants.USE_EMBEDDED_TYPESCRIPT, true);
		} else {
			node.putBoolean(TypeScriptCorePreferenceConstants.USE_EMBEDDED_TYPESCRIPT, false);
		}
		node.put(TypeScriptCorePreferenceConstants.INSTALLED_TYPESCRIPT_PATH, "");
	}

	private void initializeTsserverPreferences(IEclipsePreferences node, ITypeScriptRepository defaultRepository) {
		node.putBoolean(TypeScriptCorePreferenceConstants.TSSERVER_TRACE_ON_CONSOLE, false);
		node.putBoolean(TypeScriptCorePreferenceConstants.TSSERVER_EMULATE_PLUGINS, false);
	}

	private void initializeInstallTypesPreferences(IEclipsePreferences node) {
		node.putBoolean(TypeScriptCorePreferenceConstants.INSTALL_TYPES_ENABLE_TELEMETRY, false);
		node.putBoolean(TypeScriptCorePreferenceConstants.INSTALL_TYPES_DISABLE_ATA, false);
	}

	private void initializeSalsa(IEclipsePreferences node) {
		node.put(TypeScriptCorePreferenceConstants.USE_SALSA_AS_JS_INFERENCE, UseSalsa.WhenNoJSDTNature.name());
	}

	private void initializeTypeScriptBuildPath(IEclipsePreferences node) {
		node.put(TypeScriptCorePreferenceConstants.TYPESCRIPT_BUILD_PATH,
				TypeScriptCorePreferenceConstants.DEFAULT_TYPESCRIPT_BUILD_PATH);
	}

	private void initializeTslintPreferences(IEclipsePreferences node, ITypeScriptRepository defaultRepository) {
		node.put(TypeScriptCorePreferenceConstants.TSLINT_STRATEGY, TslintSettingsStrategy.DisableTslint.name());
		node.put(TypeScriptCorePreferenceConstants.TSLINT_USE_CUSTOM_TSLINTJSON_FILE, "");
		if (defaultRepository != null) {
			node.put(TypeScriptCorePreferenceConstants.TSLINT_EMBEDDED_TYPESCRIPT_ID, defaultRepository.getName());
			node.putBoolean(TypeScriptCorePreferenceConstants.TSLINT_USE_EMBEDDED_TYPESCRIPT, true);
		} else {
			node.putBoolean(TypeScriptCorePreferenceConstants.TSLINT_USE_EMBEDDED_TYPESCRIPT, false);
		}
		node.put(TypeScriptCorePreferenceConstants.TSLINT_INSTALLED_TYPESCRIPT_PATH, "");
	}

	private void initializeEditorFormatOptions(IEclipsePreferences node) {
		node.putBoolean(TypeScriptCorePreferenceConstants.EDITOR_OPTIONS_CONVERT_TABS_TO_SPACES,
				TypeScriptCorePreferenceConstants.EDITOR_OPTIONS_CONVERT_TABS_TO_SPACES_DEFAULT);
		node.putInt(TypeScriptCorePreferenceConstants.EDITOR_OPTIONS_INDENT_SIZE,
				TypeScriptCorePreferenceConstants.EDITOR_OPTIONS_INDENT_SIZE_DEFAULT);
		node.putInt(TypeScriptCorePreferenceConstants.EDITOR_OPTIONS_TAB_SIZE,
				TypeScriptCorePreferenceConstants.EDITOR_OPTIONS_TAB_SIZE_DEFAULT);
		node.putBoolean(TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_COMMA_DELIMITER,
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_COMMA_DELIMITER_DEFAULT);
		node.putBoolean(TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_SEMICOLON_IN_FOR_STATEMENTS,
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_SEMICOLON_IN_FOR_STATEMENTS_DEFAULT);
		node.putBoolean(TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_BEFORE_AND_AFTER_BINARY_OPERATORS,
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_BEFORE_AND_AFTER_BINARY_OPERATORS_DEFAULT);
		node.putBoolean(
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_KEYWORDS_IN_CONTROL_FLOW_STATEMENTS,
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_KEYWORDS_IN_CONTROL_FLOW_STATEMENTS_DEFAULT);
		node.putBoolean(
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_FUNCTION_KEYWORD_FOR_ANONYMOUS_FUNCTIONS,
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_FUNCTION_KEYWORD_FOR_ANONYMOUS_FUNCTIONS_DEFAULT);
		node.putBoolean(
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_OPENING_AND_BEFORE_CLOSING_NONEMPTY_PARENTHESIS,
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_OPENING_AND_BEFORE_CLOSING_NONEMPTY_PARENTHESIS_DEFAULT);
		node.putBoolean(
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_OPENING_AND_BEFORE_CLOSING_NONEMPTY_BRACKETS,
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_INSERT_SPACE_AFTER_OPENING_AND_BEFORE_CLOSING_NONEMPTY_BRACKETS_DEFAULT);
		node.putBoolean(TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_PLACE_OPEN_BRACE_ON_NEW_LINE_FOR_FUNCTIONS,
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_PLACE_OPEN_BRACE_ON_NEW_LINE_FOR_FUNCTIONS_DEFAULT);
		node.putBoolean(
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_PLACE_OPEN_BRACE_ON_NEW_LINE_FOR_CONTROL_BLOCKS,
				TypeScriptCorePreferenceConstants.FORMAT_OPTIONS_PLACE_OPEN_BRACE_ON_NEW_LINE_FOR_CONTROL_BLOCKS_DEFAULT);

	}

	/**
	 * Fix the embedded TypeScript runtime ande node.js preference if needed.
	 * 
	 * @param preferences
	 * @see https://github.com/angelozerr/typescript.java/issues/121
	 */
	public static void fixEmbeddedPreference(IEclipsePreferences preferences) {
		boolean refresh = false;
		// Fix embedded TypeScript runtime if needed
		String embeddedTypeScriptId = preferences.get(TypeScriptCorePreferenceConstants.EMBEDDED_TYPESCRIPT_ID, null);
		if (embeddedTypeScriptId != null
				&& TypeScriptCorePlugin.getTypeScriptRepositoryManager().getRepository(embeddedTypeScriptId) == null) {
			preferences.put(TypeScriptCorePreferenceConstants.EMBEDDED_TYPESCRIPT_ID,
					TypeScriptCorePlugin.getTypeScriptRepositoryManager().getDefaultRepository().getName());
			refresh = true;
		}
		// Fix embedded node.js if needed
		String embeddedNode = preferences.get(TypeScriptCorePreferenceConstants.NODEJS_EMBEDDED_ID, null);
		if (embeddedNode != null
				&& TypeScriptCorePlugin.getNodejsInstallManager().findNodejsInstall(embeddedNode) == null) {
			if (useBundledNodeJsEmbedded(preferences)) {
				refresh = true;
			}
		}
		if (refresh) {
			try {
				preferences.flush();
			} catch (BackingStoreException e) {
				Trace.trace(Trace.SEVERE, "Error while fixing embedded TypeScript runtime and node.js preference", e);
			}
		}
	}
}