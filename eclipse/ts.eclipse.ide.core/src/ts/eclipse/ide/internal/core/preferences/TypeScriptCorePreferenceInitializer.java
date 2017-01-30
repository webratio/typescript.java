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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.framework.Version;
import org.osgi.service.prefs.BackingStoreException;

import ts.cmd.tslint.TslintSettingsStrategy;
import ts.eclipse.ide.core.TypeScriptCorePlugin;
import ts.eclipse.ide.core.nodejs.IDENodejsProcessHelper;
import ts.eclipse.ide.core.nodejs.IEmbeddedNodejs;
import ts.eclipse.ide.core.nodejs.INodejsInstallManager;
import ts.eclipse.ide.core.preferences.TypeScriptCorePreferenceConstants;
import ts.eclipse.ide.core.resources.UseSalsa;
import ts.eclipse.ide.core.resources.WorkspaceTypeScriptSettingsHelper;
import ts.eclipse.ide.internal.core.Trace;
import ts.repository.ITypeScriptRepository;

/**
 * Eclipse preference initializer for TypeScript Core.
 * 
 */
public class TypeScriptCorePreferenceInitializer extends AbstractPreferenceInitializer {

	private static final String EXTENSION_TYPESCRIPT_REPOSITORIES = "typeScriptRepositories";

	@Override
	public void initializeDefaultPreferences() {
		IEclipsePreferences node = WorkspaceTypeScriptSettingsHelper
				.getWorkspaceDefaultPreferences(TypeScriptCorePlugin.PLUGIN_ID);

		// initialize properties for direct access of node.js server (start an
		// internal process)
		initializeNodejsPreferences(node);

		try {

			// Create repositories and choose the newest one as default
			List<ITypeScriptRepository> repositories = createContributedTypeScriptRepositories();
			ITypeScriptRepository defaultRepository = !repositories.isEmpty() ? repositories.get(0) : null;
			if (defaultRepository != null) {

				// Initialize tsc preferences
				initializeTypeScriptRuntimePreferences(node, defaultRepository);
				// Initialize tsserver preferences
				initializeTsserverPreferences(node, defaultRepository);
				// Initialize tslint preferences
				initializeTslintPreferences(node, defaultRepository);
			} else {
				Trace.trace(Trace.WARNING, "No default TypeScript repository is available");
			}
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
		fixEmbeddedTypeScriptIdPreference(
				WorkspaceTypeScriptSettingsHelper.getWorkspacePreferences(TypeScriptCorePlugin.PLUGIN_ID));
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
			node.put(TypeScriptCorePreferenceConstants.NODEJS_PATH, IDENodejsProcessHelper.getNodejsPath());
		} else {
			node.putBoolean(TypeScriptCorePreferenceConstants.USE_NODEJS_EMBEDDED, true);
			node.put(TypeScriptCorePreferenceConstants.NODEJS_PATH, "");
		}
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
		node.put(TypeScriptCorePreferenceConstants.EMBEDDED_TYPESCRIPT_ID, defaultRepository.getName());
		node.putBoolean(TypeScriptCorePreferenceConstants.USE_EMBEDDED_TYPESCRIPT, true);
		node.put(TypeScriptCorePreferenceConstants.INSTALLED_TYPESCRIPT_PATH, "");
	}

	private void initializeTsserverPreferences(IEclipsePreferences node, ITypeScriptRepository defaultRepository) {
		node.putBoolean(TypeScriptCorePreferenceConstants.TSSERVER_TRACE_ON_CONSOLE, false);
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
		node.put(TypeScriptCorePreferenceConstants.TSLINT_EMBEDDED_TYPESCRIPT_ID, defaultRepository.getName());
		node.putBoolean(TypeScriptCorePreferenceConstants.TSLINT_USE_EMBEDDED_TYPESCRIPT, true);
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
	 * Fix the embeddedTypeScriptId preference if needed with default
	 * embeddedTypeScriptId.
	 * 
	 * @param preferences
	 * @see https://github.com/angelozerr/typescript.java/issues/121
	 */
	public static void fixEmbeddedTypeScriptIdPreference(IEclipsePreferences preferences) {
		String embeddedTypeScriptId = preferences.get(TypeScriptCorePreferenceConstants.EMBEDDED_TYPESCRIPT_ID, null);
		if (embeddedTypeScriptId != null
				&& TypeScriptCorePlugin.getTypeScriptRepositoryManager().getRepository(embeddedTypeScriptId) == null) {
			preferences.put(TypeScriptCorePreferenceConstants.EMBEDDED_TYPESCRIPT_ID,
					TypeScriptCorePlugin.getTypeScriptRepositoryManager().getDefaultRepository().getName());
			try {
				preferences.flush();
			} catch (BackingStoreException e) {
				Trace.trace(Trace.SEVERE, "Error while fixing embeddedTypeScriptId preference", e);
			}
		}
	}

	private static List<ITypeScriptRepository> createContributedTypeScriptRepositories() {

		// loop over contributed TypeScript repositories, collecting them in a
		// list
		List<ITypeScriptRepository> repositories = new ArrayList<ITypeScriptRepository>();
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		for (IConfigurationElement cf : registry.getConfigurationElementsFor(TypeScriptCorePlugin.PLUGIN_ID,
				EXTENSION_TYPESCRIPT_REPOSITORIES)) {
			String bundleId = cf.getNamespaceIdentifier();
			try {
				File bundleDir = FileLocator.getBundleFile(Platform.getBundle(bundleId));
				if (!bundleDir.isDirectory()) {
					throw new RuntimeException("Bundle location " + bundleDir
							+ " cannot contribute a TypeScript repository because it is not a directory");
				}
				File repositoryDir = new File(bundleDir, cf.getAttribute("baseDir"));
				repositories.add(
						TypeScriptCorePlugin.getTypeScriptRepositoryManager().createDefaultRepository(repositoryDir));
			} catch (Exception e) {
				Trace.trace(Trace.SEVERE, "Error while procesing TypeScript repository contributed by " + bundleId, e);
			}
		}

		// sort repositories by version in decreasing order
		Collections.sort(repositories, new Comparator<ITypeScriptRepository>() {

			@Override
			public int compare(ITypeScriptRepository repo1, ITypeScriptRepository repo2) {
				Version v1 = extractVerion(repo1);
				Version v2 = extractVerion(repo2);
				return v2.compareTo(v1);
			}

			private Version extractVerion(ITypeScriptRepository repo) {
				try {
					return Version.parseVersion(repo.getTypesScriptVersion());
				} catch (IllegalArgumentException e) {
					return Version.emptyVersion;
				}
			}

		});

		return repositories;
	}
}