package ts.eclipse.ide.core.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.ISavedState;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;

/**
 * A container for state information that is saved along with the workspace.
 */
public class PersistedState {

	private final String pluginId;
	private final String stateId;
	private final IPath stateLocation;
	private final Map<String, Object> data;

	public PersistedState(Plugin plugin, String stateId) throws CoreException {
		this.pluginId = plugin.getBundle().getSymbolicName();
		this.stateId = Objects.requireNonNull(stateId);
		this.stateLocation = plugin.getStateLocation();

		Map<String, Object> reaData = null;
		{
			ISavedState ss = ResourcesPlugin.getWorkspace().addSaveParticipant(pluginId, new Participant());
			if (ss != null) {
				IPath saveFilePath = ss.lookup(new Path(stateId));
				if (saveFilePath != null) {
					File file = plugin.getStateLocation().append(saveFilePath).toFile();
					try (InputStream in = new FileInputStream(file);
							ObjectInputStream objIn = new ObjectInputStream(in)) {
						@SuppressWarnings("unchecked")
						Map<String, Object> typedReadDAta = (Map<String, Object>) objIn.readObject();
						reaData = typedReadDAta;
					} catch (Exception e) {
						plugin.getLog()
								.log(new Status(Status.ERROR, pluginId, "Error loading saved state " + stateId, e));
					}
				}
			}
		}
		this.data = reaData != null ? reaData : new HashMap<>();
	}

	private final class Participant implements ISaveParticipant {

		@Override
		public void prepareToSave(ISaveContext context) throws CoreException {
		}

		@Override
		public void saving(ISaveContext context) throws CoreException {
			int saveNumber = context.getSaveNumber();
			IPath saveFilePath = new Path("PersistedState/" + stateId + "-" + Integer.toString(saveNumber));
			File file = stateLocation.append(saveFilePath).toFile();
			file.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(file);
					ObjectOutputStream objOut = new ObjectOutputStream(out)) {
				synchronized (PersistedState.this.data) {
					objOut.writeObject(PersistedState.this.data);
				}
			} catch (Exception e) {
				throw new CoreException(new Status(Status.ERROR, pluginId, "Error saving state " + stateId, e));
			}
			context.map(new Path(stateId), saveFilePath);
			context.needSaveNumber();
		}

		@Override
		public void doneSaving(ISaveContext context) {
		}

		@Override
		public void rollback(ISaveContext context) {
		}

	}

	public Object get(String key, Supplier<Object> initialValueSupplier) {
		synchronized (this.data) {
			return this.data.computeIfAbsent(key, it -> initialValueSupplier.get());
		}
	}

	public Object put(String key, Object value) {
		synchronized (this.data) {
			return this.data.put(key, value);
		}
	}

	public Object remove(String key) {
		synchronized (this.data) {
			return this.data.remove(key);
		}
	}

	@Override
	public String toString() {
		return this.data.toString();
	}

}
