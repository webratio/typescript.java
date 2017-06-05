/**
 *  Copyright (c) 2015-2016 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package ts.eclipse.ide.json.ui.internal.tsconfig;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.json.jsonpath.JSONPath;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormText;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.forms.widgets.TableWrapData;

import ts.eclipse.ide.core.TypeScriptCorePlugin;
import ts.eclipse.ide.core.resources.IIDETypeScriptProject;
import ts.eclipse.ide.core.resources.ITypeScriptElementChangedListener;
import ts.eclipse.ide.core.resources.buildpath.ITypeScriptBuildPath;
import ts.eclipse.ide.core.utils.TypeScriptResourceUtil;
import ts.eclipse.ide.json.ui.AbstractFormPage;
import ts.eclipse.ide.json.ui.FormLayoutFactory;
import ts.resources.jsonconfig.TsconfigJson;

/**
 * Overview page for tsconfig.json editor.
 *
 */
public class OverviewPage extends AbstractFormPage implements ITypeScriptElementChangedListener {

	private static final String TYPESCRIPT_LINK_ID = "typescript";
	private static final String NODEJS_PREFERENCE_PAGE_ID = "ts.eclipse.ide.ui.property.NodejsPreferencePage";
	private static final String TYPESCRIPT_PREFERENCE_PAGE_ID = "ts.eclipse.ide.ui.property.TypeScriptRuntimePreferencePage";

	private static final String ID = "overview";
	private Button compileOnSave;
	private Button buildOnSave;
	private FormText formText;

	public OverviewPage(TsconfigEditor editor) {
		super(editor, ID, TsconfigEditorMessages.OverviewPage_title);
	}

	@Override
	protected boolean contributeToToolbar(IToolBarManager manager) {
		manager.add(new BuildAction((TsconfigEditor) getEditor()));
		return true;
	}

	@Override
	protected String getFormTitleText() {
		return TsconfigEditorMessages.OverviewPage_title;
	}

	@Override
	protected void createUI(IManagedForm managedForm) {
		Composite body = managedForm.getForm().getBody();
		body.setLayout(FormLayoutFactory.createFormTableWrapLayout(true, 2));
		createLeftContent(body);
		createRightContent(body);
	}

	private void createLeftContent(Composite parent) {
		FormToolkit toolkit = super.getToolkit();
		Composite left = toolkit.createComposite(parent);
		left.setLayout(FormLayoutFactory.createFormPaneTableWrapLayout(false, 1));
		left.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));
		// General Information
		createGeneralInformationSection(left);
		// Compiler section
		createCompilerSection(left);
	}

	private void createGeneralInformationSection(Composite parent) {
		FormToolkit toolkit = super.getToolkit();
		Section section = toolkit.createSection(parent, Section.DESCRIPTION | Section.TITLE_BAR);
		section.setDescription(TsconfigEditorMessages.OverviewPage_GeneralInformationSection_desc);
		section.setText(TsconfigEditorMessages.OverviewPage_GeneralInformationSection_title);
		TableWrapData data = new TableWrapData(TableWrapData.FILL_GRAB);
		section.setLayoutData(data);

		Composite body = createBody(section);

		// Target/Module
		createCombo(body, TsconfigEditorMessages.OverviewPage_target_label, new JSONPath("compilerOptions.target"),
				TsconfigJson.getAvailableTargets(), TsconfigJson.getDefaultTarget());
		createCombo(body, TsconfigEditorMessages.OverviewPage_module_label, new JSONPath("compilerOptions.module"),
				TsconfigJson.getAvailableModules());
		createCombo(body, TsconfigEditorMessages.OverviewPage_moduleResolution_label,
				new JSONPath("compilerOptions.moduleResolution"), TsconfigJson.getAvailableModuleResolutions(),
				TsconfigJson.getDefaultModuleResolution());
		// Others....
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_experimentalDecorators_label,
				new JSONPath("compilerOptions.experimentalDecorators"));

	}

	private void createCompilerSection(Composite parent) {
		FormToolkit toolkit = super.getToolkit();
		Section section = toolkit.createSection(parent, Section.DESCRIPTION | Section.TITLE_BAR);
		section.setDescription(TsconfigEditorMessages.OverviewPage_CompilerSection_desc);
		section.setText(TsconfigEditorMessages.OverviewPage_CompilerSection_title);
		TableWrapData data = new TableWrapData(TableWrapData.FILL_GRAB);
		section.setLayoutData(data);
		Composite body = createBody(section);

		final IIDETypeScriptProject tsProject = getTypeScriptProject();
		if (tsProject != null) {
			// TypeScript version + node.js
			formText = toolkit.createFormText(body, true);
			formText.setWhitespaceNormalized(true);
			updateTypeScriptNodejsVersion(tsProject);
			formText.addHyperlinkListener(new HyperlinkAdapter() {
				public void linkActivated(HyperlinkEvent e) {
					String pageId = TYPESCRIPT_LINK_ID.equals(e.getHref()) ? TYPESCRIPT_PREFERENCE_PAGE_ID
							: NODEJS_PREFERENCE_PAGE_ID;
					PreferencesUtil.createPropertyDialogOn(getSite().getShell(), tsProject.getProject(), pageId,
							new String[] { pageId }, null).open();
				}
			});
		}
		// TypeScript Builder
		boolean hasTypeScriptBuilder = tsProject != null
				? TypeScriptResourceUtil.hasTypeScriptBuilder(tsProject.getProject()) : false;

		Button typescriptBuilderCheckbox = getToolkit().createButton(body,
				TsconfigEditorMessages.OverviewPage_typeScriptBuilder_label, SWT.CHECK);
		typescriptBuilderCheckbox.setSelection(hasTypeScriptBuilder);
		typescriptBuilderCheckbox.setEnabled(tsProject != null);
		typescriptBuilderCheckbox.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				Button checkbox = (Button) e.getSource();
				if (checkbox.getSelection()) {
					try {
						TypeScriptResourceUtil.addTypeScriptBuilder(tsProject.getProject());
						updateCompileBuildOnSaveEnable(true);
					} catch (CoreException ex) {
						ErrorDialog.openError(checkbox.getShell(), TsconfigEditorMessages.TypeScriptBuilder_Error_title,
								TsconfigEditorMessages.TypeScriptBuilder_enable_Error_message, ex.getStatus());
					}
				} else {
					try {
						TypeScriptResourceUtil.removeTypeScriptBuilder(tsProject.getProject());
						updateCompileBuildOnSaveEnable(false);
					} catch (CoreException ex) {
						ErrorDialog.openError(checkbox.getShell(), TsconfigEditorMessages.TypeScriptBuilder_Error_title,
								TsconfigEditorMessages.TypeScriptBuilder_disable_Error_message, ex.getStatus());
					}
				}
			}
		});

		// Compile/Build on save
		Composite compileBuildOnSaveBody = toolkit.createComposite(body);
		compileBuildOnSaveBody.setLayout(new GridLayout());
		compileOnSave = createCheckbox(compileBuildOnSaveBody, TsconfigEditorMessages.OverviewPage_compileOnSave_label,
				new JSONPath("compileOnSave"));
		buildOnSave = createCheckbox(compileBuildOnSaveBody, TsconfigEditorMessages.OverviewPage_buildOnSave_label,
				new JSONPath("buildOnSave"));
		updateCompileBuildOnSaveEnable(hasTypeScriptBuilder);
	}

	private void updateTypeScriptNodejsVersion(final IIDETypeScriptProject tsProject) {
		formText.setText(NLS.bind(TsconfigEditorMessages.OverviewPage_typeScript_node_versions,
				tsProject.getProjectSettings().getTypeScriptVersion(), tsProject.getProjectSettings().getNodeVersion()),
				true, false);
	}

	private void updateCompileBuildOnSaveEnable(boolean hasTypeScriptBuilder) {
		compileOnSave.setEnabled(hasTypeScriptBuilder);
		buildOnSave.setEnabled(hasTypeScriptBuilder);
	}

	private void createRightContent(Composite parent) {
		FormToolkit toolkit = super.getToolkit();
		Composite right = toolkit.createComposite(parent);
		right.setLayout(FormLayoutFactory.createFormPaneTableWrapLayout(false, 1));
		right.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));
		createValidatingSection(right);
	}

	private void createValidatingSection(Composite parent) {
		FormToolkit toolkit = super.getToolkit();
		Section section = toolkit.createSection(parent, Section.DESCRIPTION | Section.TITLE_BAR);
		section.setDescription(TsconfigEditorMessages.OverviewPage_ValidatingSection_desc);
		section.setText(TsconfigEditorMessages.OverviewPage_ValidatingSection_title);
		TableWrapData data = new TableWrapData(TableWrapData.FILL_GRAB);
		section.setLayoutData(data);

		Composite body = createBody(section);

		createCheckbox(body, TsconfigEditorMessages.OverviewPage_noImplicitAny_label,
				new JSONPath("compilerOptions.noImplicitAny"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_noImplicitThis_label,
				new JSONPath("compilerOptions.noImplicitThis"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_noUnusedLocals_label,
				new JSONPath("compilerOptions.noUnusedLocals"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_noUnusedParameters_label,
				new JSONPath("compilerOptions.noUnusedParameters"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_skipDefaultLibCheck_label,
				new JSONPath("compilerOptions.skipDefaultLibCheck"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_skipLibCheck_label,
				new JSONPath("compilerOptions.skipLibCheck"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_suppressExcessPropertyErrors_label,
				new JSONPath("compilerOptions.suppressExcessPropertyErrors"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_suppressImplicitAnyIndexErrors_label,
				new JSONPath("compilerOptions.suppressImplicitAnyIndexErrors"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_allowUnusedLabels_label,
				new JSONPath("compilerOptions.allowUnusedLabels"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_noImplicitReturns_label,
				new JSONPath("compilerOptions.noImplicitReturns"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_noFallthroughCasesInSwitch_label,
				new JSONPath("compilerOptions.noFallthroughCasesInSwitch"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_allowUnreachableCode_label,
				new JSONPath("compilerOptions.allowUnreachableCode"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_forceConsistentCasingInFileNames_label,
				new JSONPath("compilerOptions.forceConsistentCasingInFileNames"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_allowSyntheticDefaultImports_label,
				new JSONPath("compilerOptions.allowSyntheticDefaultImports"));
		createCheckbox(body, TsconfigEditorMessages.OverviewPage_strictNullChecks_label,
				new JSONPath("compilerOptions.strictNullChecks"));
	}

	private Composite createBody(Section section) {
		FormToolkit toolkit = super.getToolkit();
		Composite body = toolkit.createComposite(section);
		section.setClient(body);

		GridLayout glayout = new GridLayout();
		glayout.numColumns = 1;
		body.setLayout(glayout);
		return body;
	}

	private IIDETypeScriptProject getTypeScriptProject() {
		IFile tsconfigFile = getTsconfigFile();
		if (tsconfigFile == null) {
			return null;
		}
		try {
			IIDETypeScriptProject tsProject = TypeScriptResourceUtil.getTypeScriptProject(tsconfigFile.getProject());
			TypeScriptCorePlugin.getDefault().addTypeScriptElementChangedListener(this);
			return tsProject;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}

	private IFile getTsconfigFile() {
		return getEditor().getFile();
	}

	@Override
	public void dispose() {
		super.dispose();
		TypeScriptCorePlugin.getDefault().removeTypeScriptElementChangedListener(this);
	}

	@Override
	public void typeScriptVersionChanged(IIDETypeScriptProject tsProject, String oldVersion, String newVersion) {
		updateTypeScriptNodejsVersion(tsProject);
	}

	@Override
	public void nodejsVersionChanged(IIDETypeScriptProject tsProject, String oldVersion, String newVersion) {
		updateTypeScriptNodejsVersion(tsProject);
	}

	@Override
	public void buildPathChanged(IIDETypeScriptProject tsProject, ITypeScriptBuildPath newBuildPath,
			ITypeScriptBuildPath oldBuildPath) {
	}
}
