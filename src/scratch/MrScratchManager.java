package scratch;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.Function;
import scratch.filesystem.FileSystem;
import scratch.ide.Ide;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.*;

/**
 * User: dima
 * Date: 10/02/2013
 */
public class MrScratchManager {
	private final Ide ide;
	private final FileSystem fileSystem;
	private ScratchConfig config;

	public MrScratchManager(Ide ide, FileSystem fileSystem, ScratchConfig config) {
		this.ide = ide;
		this.fileSystem = fileSystem;
		this.config = config;
	}

	public void migrate(List<String> scratchTexts) {
		List<Integer> indexes = newArrayList();
		List<Scratch> scratchesInfo = newArrayList();

		for (int i = 1; i <= scratchTexts.size(); i++) {
			String scratchName = (i == 1 ? "&scratch" : "scratch&" + i);
			Scratch scratch = new Scratch(scratchName, "txt");

			boolean wasCreated = fileSystem.createFile(scratch.asFileName(), scratchTexts.get(i - 1));
			if (wasCreated) {
				scratchesInfo.add(scratch);
			} else {
				indexes.add(i);
			}
		}

		if (indexes.isEmpty()) {
			ide.migratedScratchesToFiles();
		} else {
			ide.failedToMigrateScratchesToFiles(indexes);
		}
		update(config.with(scratchesInfo).needsMigration(false));
	}

	public void userWantsToSeeScratchesList(UserDataHolder userDataHolder) {
		final List<String> fileNames = fileSystem.listScratchFiles();

		final List<Scratch> oldScratches = findAll(config.scratches, new Condition<Scratch>() {
			@Override public boolean value(Scratch it) {
				return fileNames.contains(it.asFileName());
			}
		});
		Condition<String> whichAreNewFiles = new Condition<String>() {
			@Override public boolean value(final String fileName) {
				return !exists(oldScratches, new Condition<Scratch>() {
					@Override public boolean value(Scratch scratchInfo) {
						return fileName.equals(scratchInfo.asFileName());
					}
				});
			}
		};
		List<String> newFileNames = filter(fileNames, whichAreNewFiles);
		List<Scratch> newScratches = map(newFileNames, new Function<String, Scratch>() {
			@Override public Scratch fun(String it) {
				return Scratch.createFrom(it);
			}
		});

		List<Scratch> scratches = concat(oldScratches, newScratches);
		if (!newScratches.isEmpty() || oldScratches.size() != config.scratches.size()) {
			update(config.with(scratches));
		}
		ide.displayScratchesListPopup(scratches, userDataHolder);
	}

	public void userWantsToOpenScratch(Scratch scratch, UserDataHolder userDataHolder) {
		if (fileSystem.scratchFileExists(scratch.asFileName()))
			ide.openScratch(scratch, userDataHolder);
		else
			ide.failedToOpen(scratch);
	}

	public void userWantsToOpenDefaultScratch(UserDataHolder userDataHolder) {
		if (config.scratches.isEmpty()) {
			ide.failedToOpenDefaultScratch();
		} else {
			Scratch scratch = config.scratches.get(0);
			if (fileSystem.scratchFileExists(scratch.asFileName())) {
				ide.openScratch(scratch, userDataHolder);
			} else {
				ide.failedToOpenDefaultScratch();
			}
		}
	}

	public boolean canUserRename(final Scratch scratch, String fullNameWithMnemonics) {
		final Scratch renamedScratch = Scratch.createFrom(fullNameWithMnemonics);

		return !exists(config.scratches, new Condition<Scratch>() {
			@Override public boolean value(Scratch it) {
				return !it.equals(scratch) && it.name.equals(renamedScratch.name);
			}
		});
	}

	public void userWantsToRename(Scratch scratch, String fullNameWithMnemonics) {
		Scratch renamedScratch = Scratch.createFrom(fullNameWithMnemonics);

		boolean wasRenamed = fileSystem.renameFile(scratch.asFileName(), renamedScratch.asFileName());
		if (wasRenamed) {
			update(config.replace(scratch, renamedScratch));
		} else {
			ide.failedToRename(scratch);
		}
	}

	public void userMovedScratch(final Scratch scratch, int shift) {
		update(config.move(scratch, shift));
	}

	public void userWantsToListenToClipboard(boolean value) {
		update(config.listenToClipboard(value));
	}

	public void clipboardListenerWantsToAddTextToScratch(String clipboardText) {
		if (config.scratches.isEmpty()) {
			ide.failedToOpenDefaultScratch();
		} else {
			Scratch scratch = config.scratches.get(0);
			if (fileSystem.scratchFileExists(scratch.asFileName())) {
				ide.addTextTo(scratch, clipboardText, config.clipboardAppendType);
			} else {
				ide.failedToOpenDefaultScratch();
			}
		}
	}

	public boolean shouldListenToClipboard() {
		return config.listenToClipboard;
	}

	private void update(ScratchConfig newConfig) {
		config = newConfig;
		ide.updateConfig(config);
	}
}
