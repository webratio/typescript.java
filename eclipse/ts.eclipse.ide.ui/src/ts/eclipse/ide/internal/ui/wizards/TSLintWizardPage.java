package ts.eclipse.ide.internal.ui.wizards;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ControlEnableState;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import ts.cmd.tsc.CompilerOptions;
import ts.cmd.tsc.Plugin;
import ts.eclipse.ide.core.TypeScriptCorePlugin;
import ts.eclipse.ide.internal.ui.TypeScriptUIMessages;
import ts.eclipse.ide.ui.widgets.NpmInstallWidget;
import ts.eclipse.ide.ui.wizards.AbstractWizardPage;
import ts.repository.ITypeScriptRepository;
import ts.resources.jsonconfig.TsconfigJson;

public class TSLintWizardPage extends AbstractWizardPage {

	private static final String PAGE_NAME = "TSLintWizardPage";

	private ControlEnableState fBlockEnableState;
	private Button enableTslint;
	private Composite controlsComposite;

	// tslint Runtime
	private Button useEmbeddedTslintRuntimeButton;
	private boolean useEmbeddedTslintRuntime;
	private Combo embeddedTslintRuntime;
	private NpmInstallWidget installTslintRuntime;

	// tslint Plugin
	private Button useEmbeddedTslintPluginButton;
	private boolean useEmbeddedTslintPlugin;
	private Combo embeddedTslintPlugin;
	private NpmInstallWidget installTslintPlugin;

	protected TSLintWizardPage() {
		super(PAGE_NAME, TypeScriptUIMessages.TSLintWizardPage_title, null);
		super.setDescription(TypeScriptUIMessages.TSLintWizardPage_description);
	}

	@Override
	protected void createBody(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));

		enableTslint = new Button(composite, SWT.CHECK);
		enableTslint.setText(TypeScriptUIMessages.TSLintWizardPage_enableTslint_text);
		enableTslint.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				enableTslintContent(enableTslint.getSelection());
			}
		});

		controlsComposite = new Composite(composite, SWT.NONE);
		controlsComposite.setFont(composite.getFont());
		controlsComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 1;
		controlsComposite.setLayout(layout);
		createTslintRuntimeBody(controlsComposite);
		createTslintPluginBody(controlsComposite);
	}

	// ------------------- tslint Runtime content

	private void createTslintRuntimeBody(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setFont(parent.getFont());
		group.setText(TypeScriptUIMessages.TSLintWizardPage_tslint_group_label);
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		int nColumns = 2;
		GridLayout layout = new GridLayout();
		layout.numColumns = nColumns;
		group.setLayout(layout);

		// Embedded tslint
		createEmbeddedTslintField(group);
		// Install Typetslint
		createInstallTslintField(group);
	}

	private void createEmbeddedTslintField(Composite parent) {
		useEmbeddedTslintRuntimeButton = new Button(parent, SWT.RADIO);
		useEmbeddedTslintRuntimeButton.setText(TypeScriptUIMessages.TSLintWizardPage_useEmbeddedTslintRuntime_label);
		useEmbeddedTslintRuntimeButton.addListener(SWT.Selection, this);
		useEmbeddedTslintRuntimeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateTslintRuntimeMode();
			}
		});

		embeddedTslintRuntime = new Combo(parent, SWT.READ_ONLY);
		embeddedTslintRuntime.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		ComboViewer viewer = new ComboViewer(embeddedTslintRuntime);
		viewer.setContentProvider(ArrayContentProvider.getInstance());
		viewer.setLabelProvider(new TypeScriptRepositoryLabelProvider(true, false));
		List<ITypeScriptRepository> repositories = Arrays
				.stream(TypeScriptCorePlugin.getTypeScriptRepositoryManager().getRepositories())
				.filter(r -> r.getTslintFile() != null).collect(Collectors.toList());
		viewer.setInput(repositories);
	}

	private void createInstallTslintField(Composite parent) {
		Button useInstallTslintRuntime = new Button(parent, SWT.RADIO);
		useInstallTslintRuntime.setText(TypeScriptUIMessages.TSLintWizardPage_useInstallTslintRuntime_label);
		useInstallTslintRuntime.addListener(SWT.Selection, this);
		useInstallTslintRuntime.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateTslintRuntimeMode();
			}
		});
		installTslintRuntime = new NpmInstallWidget("tslint", this, parent, SWT.NONE);
		installTslintRuntime.getVersionText().addListener(SWT.Modify, this);
	}

	private void updateTslintRuntimeMode() {
		useEmbeddedTslintRuntime = useEmbeddedTslintRuntimeButton.getSelection();
		embeddedTslintRuntime.setEnabled(useEmbeddedTslintRuntime);
		installTslintRuntime.setEnabled(!useEmbeddedTslintRuntime);
	}

	private IStatus validateTslintRuntime() {
		if (useEmbeddedTslintRuntimeButton.getSelection()) {
			return Status.OK_STATUS;
		}
		return installTslintRuntime.getStatus();
	}

	// ------------------- tslint Plugin content

	private void createTslintPluginBody(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setFont(parent.getFont());
		group.setText(TypeScriptUIMessages.TSLintWizardPage_tslintPlugin_group_label);
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		int nColumns = 2;
		GridLayout layout = new GridLayout();
		layout.numColumns = nColumns;
		group.setLayout(layout);

		// Embedded tslint plugin
		createEmbeddedTslintPluginField(group);
		// Install tslint plugin
		createInstallTslintPluginField(group);
	}

	private void createEmbeddedTslintPluginField(Composite parent) {
		useEmbeddedTslintPluginButton = new Button(parent, SWT.RADIO);
		useEmbeddedTslintPluginButton.setText(TypeScriptUIMessages.TSLintWizardPage_useEmbeddedTslintPlugin_label);
		useEmbeddedTslintPluginButton.addListener(SWT.Selection, this);
		useEmbeddedTslintPluginButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateTslintPluginMode();
			}
		});

		embeddedTslintPlugin = new Combo(parent, SWT.READ_ONLY);
		embeddedTslintPlugin.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		ComboViewer viewer = new ComboViewer(embeddedTslintPlugin);
		viewer.setContentProvider(ArrayContentProvider.getInstance());
		viewer.setLabelProvider(new TypeScriptRepositoryLabelProvider(false, true));
		List<ITypeScriptRepository> repositories = Arrays
				.stream(TypeScriptCorePlugin.getTypeScriptRepositoryManager().getRepositories())
				.filter(r -> r.getTslintLanguageServiceName() != null).collect(Collectors.toList());
		viewer.setInput(repositories);
	}

	private void createInstallTslintPluginField(Composite parent) {
		Button useInstallTslintPlugin = new Button(parent, SWT.RADIO);
		useInstallTslintPlugin.setText(TypeScriptUIMessages.TSLintWizardPage_useInstallTslintPlugin_label);
		useInstallTslintPlugin.addListener(SWT.Selection, this);
		useInstallTslintPlugin.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateTslintPluginMode();
			}
		});
		installTslintPlugin = new NpmInstallWidget("tslint-language-service", this, parent, SWT.NONE);
		installTslintPlugin.getVersionText().addListener(SWT.Modify, this);
	}

	private void updateTslintPluginMode() {
		useEmbeddedTslintPlugin = useEmbeddedTslintPluginButton.getSelection();
		embeddedTslintPlugin.setEnabled(useEmbeddedTslintPlugin);
		installTslintPlugin.setEnabled(!useEmbeddedTslintPlugin);
	}

	private IStatus validateTslintPlugin() {
		if (useEmbeddedTslintPluginButton.getSelection()) {
			return Status.OK_STATUS;
		}
		return installTslintPlugin.getStatus();
	}

	@Override
	protected void initializeDefaultValues() {
		// Default values for tslint runtime
		if (embeddedTslintRuntime.getItemCount() > 0) {
			embeddedTslintRuntime.select(0);
		}
		useEmbeddedTslintRuntimeButton.setSelection(true);
		updateTslintRuntimeMode();

		if (embeddedTslintPlugin.getItemCount() > 0) {
			embeddedTslintPlugin.select(0);
		}
		useEmbeddedTslintPluginButton.setSelection(true);
		updateTslintPluginMode();

		// Disable tslint
		enableTslint.setSelection(false);
		enableTslintContent(false);
	}

	@Override
	protected IStatus[] validatePage() {
		IStatus[] status = new IStatus[2];
		status[0] = validateTslintRuntime();
		status[1] = validateTslintPlugin();
		return status;
	}

	protected void enableTslintContent(boolean enable) {
		if (enable) {
			if (fBlockEnableState != null) {
				fBlockEnableState.restore();
				fBlockEnableState = null;
			}
		} else {
			if (fBlockEnableState == null) {
				fBlockEnableState = ControlEnableState.disable(controlsComposite);
			}
		}
	}

	public String getNpmInstallCommand() {
		if (useEmbeddedTslintRuntime) {
			return null;
		}
		return installTslintRuntime.getNpmInstallCommand();
	}

	public void updateTsconfig(TsconfigJson tsconfig) {
		if (enableTslint.getSelection()) {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();
			if (compilerOptions == null) {
				compilerOptions = new CompilerOptions();
				tsconfig.setCompilerOptions(compilerOptions);
			}
			List<Plugin> plugins = compilerOptions.getPlugins();
			if (plugins == null) {
				plugins = new ArrayList<>();
				compilerOptions.setPlugins(plugins);
			}

			Plugin plugin = new Plugin();
			plugin.setName("tslint-language-service");
			plugins.add(plugin);
		}

	}

//	public void updateCommand(List<LineCommand> commands, IProject project) {
//		if (!useEmbeddedTslintRuntime) {
//			commands.add(new LineCommand(installTslintRuntime.getNpmInstallCommand()));
//			// generate a tslint.json file by using tslint toolings
//			commands.add(new LineCommand("node node_modules/tslint/bin/tslint -i", new TerminalCommandAdapter() {
//				@Override
//				public void onTerminateCommand(LineCommand lineCommand) {
//					IFile tslintJsonFile = project.getFile("tslint.json");
//					try {
//						tslintJsonFile.refreshLocal(IResource.DEPTH_INFINITE, null);
//					} catch (CoreException e) {						
//						e.printStackTrace();
//					}
//				}
//			}));
//		}
//		if (!useEmbeddedTslintPlugin) {
//			commands.add(new LineCommand(installTslintPlugin.getNpmInstallCommand()));
//		}
//	}
}
