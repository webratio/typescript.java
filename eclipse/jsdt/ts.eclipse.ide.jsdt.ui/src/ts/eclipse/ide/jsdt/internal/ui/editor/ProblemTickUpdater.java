/**
 *  Copyright (c) 2015-2016 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Lorenzo Dalla Vecchia <lorenzo.dallavecchia@webratio.com> - initial API and implementation
 */
package ts.eclipse.ide.jsdt.internal.ui.editor;

import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IEditorInput;

import ts.eclipse.ide.core.TypeScriptCorePlugin;
import ts.eclipse.ide.core.resources.problems.IProblemChangeListener;
import ts.eclipse.ide.jsdt.internal.ui.JSDTTypeScriptUIImages;
import ts.eclipse.ide.jsdt.internal.ui.JSDTTypeScriptUIPlugin;
import ts.utils.FileUtils;

final class ProblemTickUpdater {

	private final TypeScriptEditor editor;
    private final IProblemChangeListener problemChangeListener;

	ProblemTickUpdater(TypeScriptEditor editor) {
		this.editor = Objects.requireNonNull(editor);
        this.problemChangeListener = new IProblemChangeListener() {
            @Override
            public void problemsChanged(Set<IResource> changedResources) {
                handleChangedProblems(changedResources);
            }
        };
		TypeScriptCorePlugin.getProblemManager().addProblemChangedListener(problemChangeListener);
    }

    void dispose() {
		TypeScriptCorePlugin.getProblemManager().removeProblemChangedListener(problemChangeListener);
    }

    public void update() {
        updateTitleImageForResource(getEditorInputResource());
    }

    private void handleChangedProblems(Set<IResource> changedResources) {
        IResource inputResource = getEditorInputResource();
        if (changedResources.contains(inputResource)) {
            updateTitleImageForResource(inputResource);
        }
    }

    private IResource getEditorInputResource() {
        IEditorInput input = editor.getEditorInput();
        if (input == null) {
            return null;
        }
        return input.getAdapter(IResource.class);
    }

	private void updateTitleImageForResource(IResource resource) {
        if (resource == null || !resource.isAccessible()) {
            editor.updateTitleImage(null);
            return;
        }

		// Determine the current maximum problem severity
        int severity;
        try {
            severity = resource.findMaxProblemSeverity(IMarker.PROBLEM, true, 0);
        } catch (CoreException e) {
			JSDTTypeScriptUIPlugin.log(e);
            return;
        }

		// Prepare the image
		JSDTTypeScriptUIImages image;
		boolean isJs = resource.getLocation().getFileExtension().equals(FileUtils.JS_EXTENSION);
		if (severity == IMarker.SEVERITY_ERROR) {
			image = isJs ? JSDTTypeScriptUIImages.JSFILE_W_ERROR : JSDTTypeScriptUIImages.TSFILE_W_ERROR;
		} else if (severity == IMarker.SEVERITY_WARNING) {
			image = isJs ? JSDTTypeScriptUIImages.JSFILE_W_WARNING : JSDTTypeScriptUIImages.TSFILE_W_WARNING;
		} else {
			image = isJs ? JSDTTypeScriptUIImages.JSFILE : JSDTTypeScriptUIImages.TSFILE;
		}

		editor.updateTitleImage(image.get());
    }

}
