package com.creat.fonts.client.gui;

import com.creat.fonts.CreatFonts;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;



final class NativeFileDialog {
	private static final String SCRIPT = """
			$ErrorActionPreference = 'Stop'
			Add-Type -AssemblyName System.Windows.Forms
			Add-Type -AssemblyName System.Drawing
			$dialog = New-Object System.Windows.Forms.OpenFileDialog
			$dialog.Title = $env:CREATFONTS_TITLE
			$dialog.Filter = 'Font files (*.ttf;*.otf;*.ttc;*.otc)|*.ttf;*.otf;*.ttc;*.otc|All files (*.*)|*.*'
			$dialog.CheckFileExists = $true
			$dialog.CheckPathExists = $true
			$dialog.Multiselect = $false
			$dialog.RestoreDirectory = $true
			if ($env:CREATFONTS_INITIAL_DIR -and (Test-Path -LiteralPath $env:CREATFONTS_INITIAL_DIR)) {
			    $dialog.InitialDirectory = $env:CREATFONTS_INITIAL_DIR
			}
			$owner = New-Object System.Windows.Forms.Form
			$owner.Text = 'creat-fonts'
			$owner.StartPosition = 'CenterScreen'
			$owner.FormBorderStyle = 'None'
			$owner.Size = New-Object System.Drawing.Size(1, 1)
			$owner.ShowInTaskbar = $false
			$owner.Opacity = 0
			$owner.TopMost = $true
			$owner.Show()
			try {
			    $result = $dialog.ShowDialog($owner)
			} finally {
			    $owner.Close()
			}
			if ($result -eq [System.Windows.Forms.DialogResult]::OK) {
			    [System.IO.File]::WriteAllText($env:CREATFONTS_OUTPUT, $dialog.FileName, (New-Object System.Text.UTF8Encoding($false)))
			} else {
			    [System.IO.File]::WriteAllText($env:CREATFONTS_OUTPUT, '', (New-Object System.Text.UTF8Encoding($false)))
			}
			""";

	private static final boolean WINDOWS =
			System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

	private NativeFileDialog() {
	}

	static boolean isSupported() {
		return WINDOWS;
	}



	static @Nullable File chooseFontFile(String title) throws IOException {
		if (!WINDOWS) {
			return null;
		}

		Path directory = Files.createTempDirectory("creat-fonts-dialog-");
		Path script = directory.resolve("open-font-dialog.ps1");
		Path output = directory.resolve("selected-path.txt");
		try {
			Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);

			ProcessBuilder builder = new ProcessBuilder(
					"powershell.exe",
					"-NoProfile",
					"-STA",
					"-NonInteractive",
					"-ExecutionPolicy",
					"Bypass",
					"-WindowStyle",
					"Hidden",
					"-File",
					script.toString()
			);
			builder.directory(directory.toFile());
			builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			builder.environment().put("CREATFONTS_TITLE", title == null ? "" : title);
			builder.environment().put("CREATFONTS_OUTPUT", output.toString());
			builder.environment().put(
					"CREATFONTS_INITIAL_DIR",
					System.getProperty("user.home", "")
			);

			Process process = builder.start();
			if (!process.waitFor(5, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				throw new IOException("Timed out waiting for the native font chooser");
			}
			if (process.exitValue() != 0) {
				throw new IOException("Native font chooser exited with code " + process.exitValue());
			}
			if (!Files.isRegularFile(output)) {
				throw new IOException("Native font chooser did not report a result");
			}

			String selected = Files.readString(output, StandardCharsets.UTF_8).strip();
			return selected.isEmpty() ? null : new File(selected);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting for the native font chooser", exception);
		} finally {
			deleteQuietly(directory);
		}
	}

	private static void deleteQuietly(Path directory) {
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException exception) {
					CreatFonts.LOGGER.debug("Could not remove temporary dialog file {}", path, exception);
				}
			});
		} catch (IOException exception) {
			CreatFonts.LOGGER.debug("Could not remove temporary dialog directory {}", directory, exception);
		}
	}
}
