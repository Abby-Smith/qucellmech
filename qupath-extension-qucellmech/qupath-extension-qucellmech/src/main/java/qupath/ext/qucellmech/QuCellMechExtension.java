package qupath.ext.qucellmech;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.prefs.Preferences;

import javafx.scene.layout.HBox;
import java.util.List;
import java.util.stream.Collectors;

public class QuCellMechExtension implements QuPathExtension {

    private static final Logger logger =
            LoggerFactory.getLogger(QuCellMechExtension.class);

    // java.util.prefs.Preferences is built into Java and persists key-value pairs
    // on the user's machine (registry on Windows, plist on macOS, ~/.java on Linux).
    // This means the user only has to enter the pycellmech path once.
    private static final Preferences prefs =
            Preferences.userNodeForPackage(QuCellMechExtension.class);
    
    private static final String PREF_RUN_CELLPOSE = "runCellpose";
    private static final String PREF_GEOJSON_DIR  = "geojsonDir";
    private static final String PREF_PYCELLMECH_EXE = "pycellmechExePath";

    @Override
    public void installExtension(QuPathGUI qupath) {
        // getMenu creates the menu if it doesn't exist, or returns it if it does.
        // The ">" separator means "QuCellMech" is a submenu of "Extensions".
        var menu    = qupath.getMenu("Extensions>QuCellMech", true);
        var runItem = new MenuItem("Run on project");
        runItem.setOnAction(e -> showDialog(qupath));
        menu.getItems().add(runItem);
    }

    private void showDialog(QuPathGUI qupath) {
        var project = qupath.getProject();
        if (project == null) {
            new Alert(Alert.AlertType.ERROR,
                    "Open a QuPath project before running QuCellMech.",
                    ButtonType.OK).show();
            return;
        }
    
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle("QuCellMech");
        dialog.setHeaderText("Configure analysis parameters");
        dialog.initOwner(qupath.getStage());
    
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(15));

        // ── Row 0: run cellpose checkbox ──────────────────────────────────────
        var cellposeCheck = new CheckBox();
        cellposeCheck.setSelected(prefs.getBoolean(PREF_RUN_CELLPOSE, true));
    
        grid.add(new Label("Run Cellpose:"), 0, 2);
        grid.add(cellposeCheck, 1, 2);
    
        // ── Row 1: GeoJSON folder (only shown when cellpose is NOT checked) ───
        // When the user already has GeoJSON annotations they want to use directly,
        // they point here instead of re-running Cellpose.

        var gjLabel     = new Label("Pre-existing GeoJSON folder:");
        var gjDirField  = new TextField(prefs.get(PREF_GEOJSON_DIR, ""));
        gjDirField.setPrefWidth(380);
    
        var browseGjBtn = new Button("Browse...");
        browseGjBtn.setOnAction(e -> {
            var chooser = new DirectoryChooser();
            chooser.setTitle("Select GeoJSON folder");
            var selected = chooser.showDialog(qupath.getStage());
            if (selected != null)
                gjDirField.setText(selected.getAbsolutePath());
        });
    
        // Start hidden if cellpose is checked
        boolean initiallyHidden = cellposeCheck.isSelected();
        gjLabel   .setVisible(!initiallyHidden);
        gjLabel   .setManaged(!initiallyHidden);
        gjDirField.setVisible(!initiallyHidden);
        gjDirField.setManaged(!initiallyHidden);
        browseGjBtn.setVisible(!initiallyHidden);
        browseGjBtn.setManaged(!initiallyHidden);
    
        // Toggle visibility when the checkbox changes
        cellposeCheck.setOnAction(e -> {
            boolean running = cellposeCheck.isSelected();
            gjLabel    .setVisible(!running);
            gjLabel    .setManaged(!running);
            gjDirField .setVisible(!running);
            gjDirField .setManaged(!running);
            browseGjBtn.setVisible(!running);
            browseGjBtn.setManaged(!running);
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        });
    
        grid.add(gjLabel,    0, 3);
        grid.add(gjDirField, 1, 3);
        grid.add(browseGjBtn,2, 3);
    
        // ── Row 2: pycellmech executable ──────────────────────────────────────
        var exeField = new TextField(prefs.get(PREF_PYCELLMECH_EXE, ""));
        exeField.setPrefWidth(380);
    
        var browseExeBtn = new Button("Browse...");
        browseExeBtn.setOnAction(e -> {
            var chooser = new FileChooser();
            chooser.setTitle("Select pycellmech executable");
            var selected = chooser.showOpenDialog(qupath.getStage());
            if (selected != null)
                exeField.setText(selected.getAbsolutePath());
        });
    
        grid.add(new Label("pycellmech executable:"), 0, 0);
        grid.add(exeField,      1, 0);
        grid.add(browseExeBtn,  2, 0);
    
        // ── Row 3: image selection ────────────────────────────────────────────────
        
        // All images are selected by default.
        // ── Image selection list ───────────────────────────────────────────────────
        var imageNames = project.getImageList().stream()
                .map(e -> e.getImageName())
                .collect(Collectors.toList());

        var listView = new ListView<String>();
        listView.getItems().addAll(imageNames);
        listView.setPrefHeight(200);

        // MULTIPLE mode enables Ctrl+click for individual selection
        // and Shift+click to select a range
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Select all images by default
        listView.getSelectionModel().selectAll();

        var selectAllBtn   = new Button("Select all");
        var deselectAllBtn = new Button("Deselect all");
        selectAllBtn  .setOnAction(e -> listView.getSelectionModel().selectAll());
        deselectAllBtn.setOnAction(e -> listView.getSelectionModel().clearSelection());

        var btnRow = new HBox(10, selectAllBtn, deselectAllBtn);

        grid.add(new Separator(),                 0, 4, 3, 1);
        grid.add(new Label("Images to process:"), 0, 5);
        grid.add(btnRow,                          1, 5, 2, 1);
        grid.add(listView,                        0, 6, 3, 1);
    
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    
        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
    
            boolean runCellpose = cellposeCheck.isSelected();
            String  gjDir       = gjDirField.getText().strip();
            String exePath      = exeField.getText().strip();
            List<String> selectedImages = new java.util.ArrayList<>(
                listView.getSelectionModel().getSelectedItems()
            );
            
            if (selectedImages.isEmpty()) {
                new Alert(Alert.AlertType.ERROR,
                        "No images selected. Please select at least one image.",
                        ButtonType.OK).show();
                return;
            }
    
            // Validate: if skipping cellpose, a GeoJSON folder must be provided
            if (!runCellpose && gjDir.isEmpty()) {
                new Alert(Alert.AlertType.ERROR,
                        "Please provide a GeoJSON folder when not running Cellpose.",
                        ButtonType.OK).show();
                return;
            }
    
            // Persist for next session
            prefs.putBoolean(PREF_RUN_CELLPOSE,   runCellpose);
            prefs.put(PREF_GEOJSON_DIR,           gjDir);
            prefs.put(PREF_PYCELLMECH_EXE,       exePath);
    
            String projectBaseDir = project.getPath()
                    .toAbsolutePath()
                    .getParent()
                    .toString();
    
            // If cellpose is running, gjDir is the standard output folder.
            // If skipping cellpose, gjDir is what the user selected above.
            String resolvedGjDir = runCellpose
                    ? projectBaseDir + "/output/geojson/"
                    : gjDir;
    
            var thread = new Thread(
                    () -> runScript(runCellpose, resolvedGjDir, exePath, selectedImages, projectBaseDir),
                    "qucellmech-runner"
            );
            thread.setDaemon(true);
            thread.start();
        });
    }

    private void runScript(boolean runCellpose, String gjDir, String pycellmechExePath, List<String> selectedImageNames,
        String projectBaseDir) {
            try {
                // Load the Groovy script from inside the JAR.
                // The path here must match exactly where you put qucellmech.groovy
                // under src/main/resources/.
                String scriptText;
                try (InputStream in = getClass().getResourceAsStream(
                        "/qupath/ext/qucellmech/qucellmech.groovy")) {
                    if (in == null)
                        throw new IOException(
                            "qucellmech.groovy not found inside the JAR.\n" +
                            "Expected location: " +
                            "src/main/resources/qupath/ext/qucellmech/qucellmech.groovy");
                    scriptText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }

            String prependedImports =
            "import qupath.lib.regions.ImagePlane\n" +
            "import qupath.opencv.ops.ImageOps\n" +
            "import static qupath.lib.scripting.QP.*\n";
            
            // Binding variables to be readable inside the Groovy script as plain variables.
            var binding = new Binding();
            binding.setVariable("runCellpose",       runCellpose);
            binding.setVariable("gjDir",             gjDir);
            binding.setVariable("pycellmechExePath", pycellmechExePath);
            binding.setVariable("selectedImageNames",  selectedImageNames);;
            binding.setVariable("PROJECT_BASE_DIR",  projectBaseDir);
            
            var shell = new GroovyShell(getClass().getClassLoader(), binding);
            shell.evaluate(prependedImports + scriptText);
            
            // output alert from QuPath displaying csv feature path
            final String csvOutputPath = projectBaseDir + "/output/csv/";
            Platform.runLater(() ->
            new Alert(Alert.AlertType.INFORMATION,
                "QuCellMech finished successfully. \nCSV feature files saved to " + csvOutputPath, ButtonType.OK).show());

            } catch (Exception e) {
            logger.error("QuCellMech script failed", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            Platform.runLater(() ->
            new Alert(Alert.AlertType.ERROR,
                "QuCellMech failed:\n\n" + msg, ButtonType.OK).show());
            }
        }

    @Override
    public String getName() { return "QuCellMech"; }

    @Override
    public String getDescription() {
        return "Cellpose nucleus detection and pycellmech geometric analysis";
    }
}