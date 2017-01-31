package ts.eclipse.ide.jsdt.internal.ui;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;

/**
 * Image registry for JSDT TypeScript UI plugin.
 */
public enum JSDTTypeScriptUIImages {
	
	TSFILE("icons/full/obj16/ts.png"),

	TSFILE_W_ERROR(TSFILE, null, null, getSharedImageDescriptor(ISharedImages.IMG_DEC_FIELD_ERROR), null),
	
	TSFILE_W_WARNING(TSFILE, null, null, getSharedImageDescriptor(ISharedImages.IMG_DEC_FIELD_WARNING), null),
	
	;
	
	private JSDTTypeScriptUIImages(String path) {
		Bundle bundle = JSDTTypeScriptUIPlugin.getDefault().getBundle();
		ImageDescriptor descr = AbstractUIPlugin.imageDescriptorFromPlugin(bundle.getSymbolicName(), path);
		JFaceResources.getImageRegistry().put(key(), descr);
	}

	private JSDTTypeScriptUIImages(JSDTTypeScriptUIImages base, ImageDescriptor tlOverlay, ImageDescriptor trOverlay, ImageDescriptor blOverlay, ImageDescriptor brOverlay) {
		ImageDescriptor descr = new DecorationOverlayIcon(base.get(),
				new ImageDescriptor[] { tlOverlay, trOverlay, blOverlay, brOverlay });
		JFaceResources.getImageRegistry().put(key(), descr);
	}

	private static ImageDescriptor getSharedImageDescriptor(String key) {
		return PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(key);
	}

	private String key() {
		return JSDTTypeScriptUIImages.class.getName() + "." + name();
	}

	/**
	 * Gets the image object associated with this image type.
	 * 
	 * @return
	 */
	public Image get() {
		return JFaceResources.getImageRegistry().get(key());
	}

	/**
	 * Gets the image descriptor object associated with this image type.
	 * 
	 * @return
	 */
	public ImageDescriptor getDescriptor() {
		return JFaceResources.getImageRegistry().getDescriptor(key());
	}

}
