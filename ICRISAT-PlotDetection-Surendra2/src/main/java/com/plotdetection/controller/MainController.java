


package com.plotdetection.controller;

import com.plotdetection.detection.OpenCVDetector;
import com.plotdetection.model.CropInfo;
import com.plotdetection.model.ImageData;
import com.plotdetection.model.PlotData;
import com.plotdetection.service.BiomassDataService;
import com.plotdetection.service.CropDataService;
import com.plotdetection.service.DatabaseService;
import com.plotdetection.service.DetectionService;
import com.plotdetection.service.ImageService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import javafx.util.Duration;
import javafx.geometry.Point2D;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class MainController {

    // ===== FXML Injected Fields =====
    @FXML private ImageView imageView;
    @FXML private ScrollPane scrollPane;
    @FXML private Pane overlayPane;
    @FXML private ListView<ImageData> imageListView;
    
    // Plot file indicator
    @FXML private HBox uploadedFileBox;
    @FXML private Label uploadedFileLabel;
    @FXML private Button removeFileButton;
    
    // Crop file indicator
    @FXML private HBox cropFileBox;
    @FXML private Label cropFileLabel;
    @FXML private Button cropRemoveButton;
    
    // Biomass file indicator
    @FXML private HBox biomassFileBox;
    @FXML private Label biomassFileLabel;
    @FXML private Button biomassRemoveButton;

    @FXML private Label plotIdLabel;
    @FXML private Label plotX1Label, plotY1Label, plotX2Label, plotY2Label;
    @FXML private Label plotWidthLabel, plotHeightLabel, plotAreaLabel;
    @FXML private Label statusLabel;

    @FXML private ComboBox<String> cropNameField;
    @FXML private ComboBox<String> cropTypeCombo;
    @FXML private ComboBox<String> nitrogenCombo;
    @FXML private TextField biomassField;
    @FXML private Label excelBiomassLabel;

    // ===== Services =====
    private ImageService imageService = new ImageService();
    private DatabaseService dbService = new DatabaseService();
    private DetectionService detectionService = new DetectionService(new OpenCVDetector());
    private CropDataService cropDataService = new CropDataService();
    private BiomassDataService biomassDataService = new BiomassDataService();

    // ===== Data =====
    private Mat currentMat;
    private String currentImageName;
    private ImageData currentImageData;
    private List<PlotData> detectedPlots = new ArrayList<>();
    private List<PlotData> manualPlots = new ArrayList<>();
    private List<PlotData> importedPlots = new ArrayList<>();
    private List<PlotData> currentPlots = new ArrayList<>();
    private Map<String, List<PlotData>> importedPlotsMap = new HashMap<>();
    private PlotData selectedPlot;
    private Map<String, Rectangle> plotRectangles = new HashMap<>();
    private List<String> uploadedFileNames = new ArrayList<>();

    // ===== Drag-to-Draw =====
    private boolean isAddingPlot = false;
    private Rectangle currentRect;

    // ===== Resize State =====
    private enum ResizeDirection { NONE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private ResizeDirection resizeDirection = ResizeDirection.NONE;
    private double resizeStartX, resizeStartY;
    private double originalX1, originalY1, originalX2, originalY2;
    private PlotData resizingPlot;
    private Rectangle activeRectangle;
    private boolean resizing = false;

    // ===== Undo/Redo =====
    private Stack<AppState> undoStack = new Stack<>();
    private Stack<AppState> redoStack = new Stack<>();
    private boolean isUndoRedoInProgress = false;

    private static class AppState {
        List<PlotData> detectedPlots;
        List<PlotData> manualPlots;
        List<PlotData> importedPlots;
        List<PlotData> currentPlots;
        PlotData selectedPlot;
        Map<String, List<PlotData>> importedPlotsMap;
    }

    // ============================================================
    //  INITIALIZATION
    // ============================================================
    @FXML
    public void initialize() {
        // ---- ListView with delete buttons ----
        imageListView.setCellFactory(lv -> new javafx.scene.control.ListCell<ImageData>() {
            @Override
            protected void updateItem(ImageData item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label fileNameLabel = new Label(item.getFile().getName());
                    Button deleteBtn = new Button("✕");
                    deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #f44336; -fx-font-weight: bold; -fx-cursor: hand;");
                    deleteBtn.setOnAction(e -> {
                        imageListView.getItems().remove(item);
                        if (currentImageData != null && currentImageData.getFile().equals(item.getFile())) {
                            onClear(null);
                        }
                        statusLabel.setText("Status: Removed " + item.getFile().getName());
                    });
                    HBox hbox = new HBox(10, fileNameLabel, deleteBtn);
                    hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    setGraphic(hbox);
                    setText(null);
                }
            }
        });

        // ---- Selection listener ----
        imageListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) loadImageData(newVal);
                }
        );

        // ---- OverlayPane event handlers ----
        overlayPane.setOnMousePressed(this::onOverlayMousePressed);
        overlayPane.setOnMouseDragged(this::onOverlayMouseDragged);
        overlayPane.setOnMouseReleased(this::onOverlayMouseReleased);
        overlayPane.setOnMouseClicked(this::onOverlayMouseClicked);

        // ---- ComboBoxes ----
        cropTypeCombo.setItems(FXCollections.observableArrayList(
                "Pigeon Pea", "Cow Pea", "Mung Bean", "Common Bean",
                "Ground Nut", "Chick Pea", "Finger Millet", "Pearl Millet",
                "Maize", "Sorghum"
        ));
        nitrogenCombo.setItems(FXCollections.observableArrayList(
                "Low", "No", "Standard"
        ));

        // ---- Resize overlayPane along with ImageView ----
        scrollPane.viewportBoundsProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                double w = newVal.getWidth();
                double h = newVal.getHeight();
                imageView.setFitWidth(w);
                imageView.setFitHeight(h);
                overlayPane.setPrefWidth(w);
                overlayPane.setPrefHeight(h);
                updateOverlays();
            }
        });
        double w = scrollPane.getViewportBounds().getWidth();
        double h = scrollPane.getViewportBounds().getHeight();
        imageView.setFitWidth(w);
        imageView.setFitHeight(h);
        overlayPane.setPrefWidth(w);
        overlayPane.setPrefHeight(h);
        imageView.setPreserveRatio(true);

        // ---- Hide all file indicators initially ----
        uploadedFileBox.setVisible(false);
        uploadedFileBox.setManaged(false);
        cropFileBox.setVisible(false);
        cropFileBox.setManaged(false);
        biomassFileBox.setVisible(false);
        biomassFileBox.setManaged(false);

        // ---- Copy Plot ID when clicked ----
        plotIdLabel.setOnMouseClicked(e -> {
            String plotId = plotIdLabel.getText();
            if (plotId != null && !plotId.isEmpty()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(plotId);
                Clipboard.getSystemClipboard().setContent(content);
                statusLabel.setText("Copied Plot ID: " + plotId);
            }
        });

        // ---- UNDO/REDO KEYBOARD SHORTCUTS ----
        imageView.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(e -> {
                    if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                        onUndo(null);
                        e.consume();
                    } else if (e.isControlDown() && e.getCode() == KeyCode.Y) {
                        onRedo(null);
                        e.consume();
                    }
                });
            }
        });
    }

    // ============================================================
    //  UPLOAD (CSV / JSON) – Plot Data
    // ============================================================
    @FXML
    public void onImportCSV(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showOpenDialog(getStage());
        if (file != null) {
            try {
                parseCSV(file);
                int total = countTotalPlots();
                uploadedFileLabel.setText(file.getName());
                uploadedFileBox.setVisible(true);
                uploadedFileBox.setManaged(true);
                statusLabel.setText("Status: Imported CSV: " + file.getName() + " (" + total + " plots)");
            } catch (Exception e) {
                e.printStackTrace();
                statusLabel.setText("Status: Failed to import CSV.");
                Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage());
                alert.showAndWait();
            }
        }
    }

    @FXML
    public void onImportJSON(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = chooser.showOpenDialog(getStage());
        if (file != null) {
            try {
                parseJSON(file);
                int total = countTotalPlots();
                uploadedFileLabel.setText(file.getName());
                uploadedFileBox.setVisible(true);
                uploadedFileBox.setManaged(true);
                statusLabel.setText("Status: Imported JSON: " + file.getName() + " (" + total + " plots)");
            } catch (Exception e) {
                e.printStackTrace();
                statusLabel.setText("Status: Failed to import JSON.");
                Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage());
                alert.showAndWait();
            }
        }
    }

    // ============================================================
    //  IMPORT CROP DATA
    // ============================================================
    @FXML
    private void onImportCropData(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel/CSV", "*.xlsx", "*.xls", "*.csv")
        );
        File file = chooser.showOpenDialog(getStage());
        if (file == null) return;

        try {
            cropDataService.loadCropData(file);
            cropFileLabel.setText(file.getName());
            cropFileBox.setVisible(true);
            cropFileBox.setManaged(true);
            statusLabel.setText("Crop data imported successfully.");
            if (currentImageData != null) {
                loadCropNamesForCurrentImage();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Failed to import crop data.");
            Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            alert.showAndWait();
        }
    }

    // ============================================================
    //  IMPORT BIOMASS DATA
    // ============================================================
    @FXML
    private void onImportBiomassData(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel/CSV", "*.xlsx", "*.xls", "*.csv")
        );
        File file = chooser.showOpenDialog(getStage());
        if (file == null) return;

        try {
            biomassDataService.loadBiomassData(file);
            biomassFileLabel.setText(file.getName());
            biomassFileBox.setVisible(true);
            biomassFileBox.setManaged(true);
            statusLabel.setText("Biomass imported successfully.");
            if (selectedPlot != null) {
                updateBiomassFromFile(selectedPlot);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Failed to import biomass.");
            Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            alert.showAndWait();
        }
    }

    // ============================================================
    //  REMOVE FILE INDICATORS (Cross buttons)
    // ============================================================
    @FXML
    public void onRemoveUploadedFile(ActionEvent event) {
        importedPlotsMap.clear();
        importedPlots.clear();
        detectedPlots.clear();
        manualPlots.clear();
        currentPlots.clear();
        selectedPlot = null;
        plotRectangles.clear();
        overlayPane.getChildren().clear();
        clearPlotDetails();
        uploadedFileBox.setVisible(false);
        uploadedFileBox.setManaged(false);
        uploadedFileLabel.setText("");
        undoStack.clear();
        redoStack.clear();
        updateOverlays();
        statusLabel.setText("Plot data removed.");
    }

    @FXML
    private void onRemoveCropFile(ActionEvent event) {

        // Clear loaded crop file
        cropDataService.clear();

        // Clear UI
        cropNameField.getItems().clear();
        cropNameField.getSelectionModel().clearSelection();
        cropTypeCombo.getSelectionModel().clearSelection();
        nitrogenCombo.getSelectionModel().clearSelection();

        biomassField.clear();
        excelBiomassLabel.setText("-");

        // Clear crop information stored in plots
        for (PlotData plot : currentPlots) {
            plot.setCropName(null);
            plot.setCropType(null);
            plot.setNitrogenLevel(null);
            plot.setBiomass(0.0);
        }

        selectedPlot = null;

        clearPlotDetails();

        updateOverlays();

        cropFileBox.setVisible(false);
        cropFileBox.setManaged(false);
        cropFileLabel.setText("");

        statusLabel.setText("Crop data removed.");
    }
    @FXML
    private void onRemoveBiomassFile(ActionEvent event) {
        biomassDataService.clear();
        biomassField.clear();
        excelBiomassLabel.setText("-");
        biomassFileBox.setVisible(false);
        biomassFileBox.setManaged(false);
        biomassFileLabel.setText("");
        undoStack.clear();
        redoStack.clear();
        statusLabel.setText("Biomass data removed.");
    }

    // ============================================================
    //  LOAD CROP NAMES FOR CURRENT IMAGE
    // ============================================================
    private void loadCropNamesForCurrentImage() {
        if (currentImageName == null) return;

        String imageId = currentImageName
                .replaceAll("\\.[^.]+$", "")
                .replaceAll("[^0-9]", "");
        String harvestName = getHarvestName();

        //List<CropInfo> crops = cropDataService.getCrops(harvestName, imageId);
        List<CropInfo> crops = cropDataService.getCrops(harvestName, imageId);

        if (crops.isEmpty()) {
            cropNameField.getItems().clear();
            return;
        }
        if (crops == null || crops.isEmpty()) {
            cropNameField.getItems().clear();
            statusLabel.setText("No crops found for image ID: " + imageId);
            return;
        }

        cropNameField.getItems().setAll(
                crops.stream().map(CropInfo::getCropName).collect(Collectors.toList())
        );
        cropNameField.getSelectionModel().clearSelection();
        statusLabel.setText("Loaded " + crops.size() + " crops for image " + imageId);
    }

    // ============================================================
    //  CROP SELECTED ACTION
    // ============================================================
    @FXML
    private void onCropSelected(ActionEvent event) {
        String cropName = cropNameField.getSelectionModel().getSelectedItem();
        if (cropName == null || cropName.isEmpty()) return;

        String imageId = currentImageName
                .replaceAll("\\.[^.]+$", "")
                .replaceAll("[^0-9]", "");
        String harvestName = getHarvestName();

        List<CropInfo> crops = cropDataService.getCrops(harvestName, imageId);
        if (crops == null) return;

        CropInfo selectedCrop = crops.stream()
                .filter(c -> c.getCropName().equals(cropName))
                .findFirst()
                .orElse(null);

        if (selectedCrop != null) {
            cropTypeCombo.getSelectionModel().select(selectedCrop.getCropType());
            nitrogenCombo.getSelectionModel().select(selectedCrop.getNitrogen());
            System.out.println("==================================");
            System.out.println("Image      : " + currentImageName);
            System.out.println("Harvest    : " + harvestName);
            System.out.println("Crop Name  : " + selectedCrop.getCropName());
            System.out.println("Crop Type  : " + selectedCrop.getCropType());
            System.out.println("Crop Code  : '" + selectedCrop.getCropCode() + "'");
            fillBiomass(harvestName, selectedCrop.getCropCode());
        }
    }

    // ============================================================
    //  BIOMASS LOOKUP (with debug)
    // ============================================================
//    private void fillBiomass(String harvest, String cropCode) {
//        System.out.println("🔍 Biomass lookup: harvest=" + harvest + ", cropCode=" + cropCode);
//        if (cropCode == null || cropCode.isEmpty()) {
//            excelBiomassLabel.setText("-");
//            biomassField.clear();
//            return;
//        }
//        Double biomass = biomassDataService.getBiomass(harvest, cropCode);
//        System.out.println("   → Found biomass: " + biomass);
//        if (biomass != null) {
//            excelBiomassLabel.setText(String.valueOf(biomass));
//            biomassField.setText(String.valueOf(biomass));
//        } else {
//            excelBiomassLabel.setText("-");
//            biomassField.clear()
//        }
//    }
    private void fillBiomass(String harvest, String cropCode) {
        System.out.println("🔍 fillBiomass: harvest=" + harvest + ", cropCode=" + cropCode);
        if (cropCode == null || cropCode.isEmpty()) {
            excelBiomassLabel.setText("-");
            biomassField.clear();
            return;
        }
        Double biomass = biomassDataService.getBiomass(harvest, cropCode);
        System.out.println("   → Retrieved biomass = " + biomass);
        if (biomass != null) {
            excelBiomassLabel.setText(String.valueOf(biomass));
            biomassField.setText(String.valueOf(biomass));
        } else {
            excelBiomassLabel.setText("-");
            biomassField.clear();
        }
    }

    private void updateBiomassFromFile(PlotData plot) {
        if (plot == null) return;
        String harvestName = getHarvestName();
        String cropCode = getCropCodeForPlot(plot);
        fillBiomass(harvestName, cropCode);
    }

//    private String getCropCodeForPlot(PlotData plot) {
//        if (plot == null || currentImageName == null) return null;
//        String imageId = currentImageName
//                .replaceAll("\\.[^.]+$", "")
//                .replaceAll("[^0-9]", "");
//        String harvestName = getHarvestName();
//        System.out.println("🔍 Plot crop: " + plot.getCropName() + ", imageId=" + imageId + ", harvest=" + harvestName);
//        List<CropInfo> crops = cropDataService.getCrops(harvestName, imageId);
//        if (crops == null) return null;
//        for (CropInfo info : crops) {
//            if (info.getCropName().equals(plot.getCropName())) {
//                System.out.println("   → Found crop code: " + info.getCropCode());
//                return info.getCropCode();
//            }
//        }
//        return null;
//    }

		private String getCropCodeForPlot(PlotData plot) {
		    if (plot == null || currentImageName == null) return null;
		    String imageId = currentImageName.replaceAll("\\.[^.]+$", "").replaceAll("[^0-9]", "");
		    String harvestName = getHarvestName();
		    System.out.println("🔍 getCropCodeForPlot: imageId=" + imageId + ", harvest=" + harvestName + ", plot crop name=" + plot.getCropName());
		    List<CropInfo> crops = cropDataService.getCrops(harvestName, imageId);
		    if (crops == null) return null;
		    for (CropInfo info : crops) {
		        if (info.getCropName().equalsIgnoreCase(plot.getCropName())) {
		            System.out.println("   → Found code: " + info.getCropCode());
		            return info.getCropCode();
		        }
		    }
		    System.out.println("   → No matching crop info found.");
		    return null;
		}

    // ============================================================
    //  PARSE CSV / JSON (Plot Data)
    // ============================================================
    private void parseCSV(File file) throws IOException {
        importedPlotsMap.clear();
        Map<String, List<PlotData>> tempMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            if (line == null) return;
            String[] headers = line.split(",");
            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colMap.put(headers[i].trim().toLowerCase(), i);
            }
            if (!colMap.containsKey("image") || !colMap.containsKey("x1") ||
                !colMap.containsKey("y1") || !colMap.containsKey("x2") ||
                !colMap.containsKey("y2")) {
                throw new IOException("CSV must have columns: image, x1, y1, x2, y2");
            }

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                try {
                    String imageName = parts[colMap.get("image")].trim();
                    int x1 = Integer.parseInt(parts[colMap.get("x1")].trim());
                    int y1 = Integer.parseInt(parts[colMap.get("y1")].trim());
                    int x2 = Integer.parseInt(parts[colMap.get("x2")].trim());
                    int y2 = Integer.parseInt(parts[colMap.get("y2")].trim());
                    double area = Math.abs(x2 - x1) * Math.abs(y2 - y1);

                    String cropName = colMap.containsKey("cropname") ? parts[colMap.get("cropname")].trim() : "";
                    String cropType = colMap.containsKey("croptype") ? parts[colMap.get("croptype")].trim() : "";
                    String nitrogen = colMap.containsKey("nitrogen") ? parts[colMap.get("nitrogen")].trim() : "";
                    double biomass = 0.0;
                    if (colMap.containsKey("biomass")) {
                        try { biomass = Double.parseDouble(parts[colMap.get("biomass")].trim()); } catch (Exception e) {}
                    }

                    String tempId = "TEMP_" + System.currentTimeMillis() + "_" + (int)(Math.random()*10000);
                    PlotData plot = new PlotData(tempId, x1, y1, x2, y2, area,
                            cropName, cropType, nitrogen, biomass);
                    tempMap.computeIfAbsent(imageName, k -> new ArrayList<>()).add(plot);
                } catch (Exception e) { /* skip */ }
            }
        }

        for (Map.Entry<String, List<PlotData>> entry : tempMap.entrySet()) {
            String imageName = entry.getKey();
            List<PlotData> plots = entry.getValue();
            String baseName = imageName.replaceAll("\\.[^.]+$", "");
            int counter = 1;
            for (PlotData plot : plots) {
                plot.setId(baseName + "_" + counter++);
            }
            importedPlotsMap.put(imageName, plots);
        }
    }

    private void parseJSON(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        importedPlotsMap.clear();

        JsonNode imagesNode = root.get("images");
        if (imagesNode == null || !imagesNode.isArray()) {
            throw new IOException("Invalid JSON: 'images' array not found.");
        }

        for (JsonNode imageNode : imagesNode) {
            String imageName = imageNode.path("image_name").asText();
            JsonNode detectedPlots = imageNode.get("detected_plots");
            if (detectedPlots == null || !detectedPlots.isArray()) continue;

            List<PlotData> plotsForImage = new ArrayList<>();
            String baseName = imageName.replaceAll("\\.[^.]+$", "");
            int counter = 1;

            for (JsonNode plotNode : detectedPlots) {
                try {
                    int x1 = plotNode.path("x1").asInt();
                    int y1 = plotNode.path("y1").asInt();
                    int x2 = plotNode.path("x2").asInt();
                    int y2 = plotNode.path("y2").asInt();
                    double area = Math.abs(x2 - x1) * Math.abs(y2 - y1);
                    String id = baseName + "_" + counter++;
                    PlotData plot = new PlotData(id, x1, y1, x2, y2, area,
                            "", "", "", 0.0);
                    plotsForImage.add(plot);
                } catch (Exception e) { /* skip */ }
            }

            if (!plotsForImage.isEmpty()) {
                importedPlotsMap.put(imageName, plotsForImage);
            }
        }
    }

    // ============================================================
    //  HELPERS
    // ============================================================
    private String getHarvestName() {
        if (currentImageData == null || currentImageData.getFile() == null) {
            return "Unknown Harvest";
        }
        File current = currentImageData.getFile().getParentFile();
        while (current != null) {
            String folderName = current.getName();
            if (folderName.matches("(?i)Harvest\\s*\\d+")) {
                return folderName;
            }
            current = current.getParentFile();
        }
        return "Unknown Harvest";
    }

    private int countTotalPlots() {
        return importedPlotsMap.values().stream().mapToInt(List::size).sum();
    }

    // ============================================================
    //  MENU / TOOLBAR ACTIONS
    // ============================================================
    @FXML
    public void onImportImage(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.tif", "*.tiff")
        );
        File file = chooser.showOpenDialog(getStage());
        if (file != null) {
            ImageData data = new ImageData(file);
            imageListView.getItems().clear();
            imageListView.getItems().add(data);
            imageListView.getSelectionModel().select(data);
            statusLabel.setText("Status: Loaded " + file.getName());
        }
    }

    @FXML
    public void onImportFolder(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        File folder = chooser.showDialog(getStage());
        if (folder != null) {
            List<ImageData> images = imageService.importFolder(folder);
            imageListView.getItems().setAll(images);
            statusLabel.setText("Status: Imported " + images.size() + " images from " + folder.getName());
            if (!images.isEmpty()) imageListView.getSelectionModel().select(0);
        }
    }

    @FXML
    public void onDetectPlots(ActionEvent event) {
        undoStack.clear();
        redoStack.clear();

        if (currentMat == null) {
            statusLabel.setText("Status: No image loaded.");
            return;
        }

        List<PlotData> importedForImage = importedPlotsMap.get(currentImageName);
        if (importedForImage != null && !importedForImage.isEmpty()) {
            importedPlots.clear();
            importedPlots.addAll(importedForImage);
            detectedPlots.clear();
            statusLabel.setText("Status: Loaded " + importedPlots.size() + " imported plots for " + currentImageName);
        } else {
            importedPlots.clear();
            detectedPlots.clear();
            List<PlotData> newDetected = detectionService.detectPlots(currentMat);
            int idx = 1;
            for (PlotData p : newDetected) {
                int x = p.getX(), y = p.getY(), w = p.getWidth(), h = p.getHeight();
                String id = "DET_" + idx++;
                PlotData newPlot = new PlotData(id, x, y, x+w, y+h, w*h,
                        p.getCropName(), p.getCropType(),
                        p.getNitrogenLevel(), p.getBiomass());
                detectedPlots.add(newPlot);
            }
            statusLabel.setText("Status: Detected " + detectedPlots.size() + " plots.");
        }
        updateOverlays();
    }

    @FXML
    public void onClear(ActionEvent event) {
        undoStack.clear();
        redoStack.clear();

        imageView.setImage(null);
        imageListView.getItems().clear();
        currentMat = null;
        currentImageData = null;
        currentImageName = null;
        detectedPlots.clear();
        manualPlots.clear();
        importedPlots.clear();
        currentPlots.clear();
        importedPlotsMap.clear();
        plotRectangles.clear();
        overlayPane.getChildren().clear();
        selectedPlot = null;
        uploadedFileBox.setVisible(false);
        uploadedFileBox.setManaged(false);
        uploadedFileLabel.setText("");
        cropFileBox.setVisible(false);
        cropFileBox.setManaged(false);
        cropFileLabel.setText("");
        biomassFileBox.setVisible(false);
        biomassFileBox.setManaged(false);
        biomassFileLabel.setText("");
        clearPlotDetails();
        statusLabel.setText("Status: Cleared all data.");
        if (currentRect != null) {
            overlayPane.getChildren().remove(currentRect);
            currentRect = null;
        }
        isAddingPlot = false;
        overlayPane.setCursor(Cursor.DEFAULT);
        resizing = false;
        activeRectangle = null;
        resizeDirection = ResizeDirection.NONE;
    }

    @FXML
    public void onSaveData(ActionEvent event) {
        if (currentPlots.isEmpty()) {
            statusLabel.setText("Status: No plots to save.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        chooser.setInitialFileName("plot_data_" + System.currentTimeMillis() + ".csv");
        File file = chooser.showSaveDialog(getStage());
        if (file == null) return;
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("id,x1,y1,x2,y2,area,type,cropName,cropType,nitrogenLevel,biomass");
            for (PlotData plot : currentPlots) {
                String type = manualPlots.contains(plot) ? "manual" :
                              importedPlots.contains(plot) ? "imported" : "detected";
                writer.printf("%s,%d,%d,%d,%d,%.2f,%s,%s,%s,%s,%.2f%n",
                        plot.getId(), plot.getX1(), plot.getY1(),
                        plot.getX2(), plot.getY2(), plot.getArea(),
                        type,
                        plot.getCropName() != null ? plot.getCropName() : "",
                        plot.getCropType() != null ? plot.getCropType() : "",
                        plot.getNitrogenLevel() != null ? plot.getNitrogenLevel() : "",
                        plot.getBiomass()
                );
            }
            statusLabel.setText("Status: Saved " + currentPlots.size() + " plots to " + file.getName());
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Status: Failed to save.");
            Alert alert = new Alert(Alert.AlertType.ERROR, "Could not save file:\n" + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    public void onExit(ActionEvent event) {
        System.exit(0);
    }

    @FXML
    public void onAbout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("Image Plot Detection Tool");
        alert.setContentText("Version 3.0\n\n" +
                "Supports:\n" +
                "- Automatic plot detection (OpenCV)\n" +
                "- Manual drag-to-draw plots\n" +
                "- Import plots from CSV/JSON\n" +
                "- Save plot data with crop details\n" +
                "- Crop and save images by plot region");
        alert.showAndWait();
    }

    // ============================================================
    //  IMAGE LOADING & OVERLAY MANAGEMENT
    // ============================================================
    private void loadImageData(ImageData data) {
        currentImageData = data;
        currentImageName = data.getFile().getName();
        Image img = imageService.loadImage(data);
        imageView.setImage(img);
        detectedPlots.clear();
        manualPlots.clear();
        importedPlots.clear();
        currentPlots.clear();
        selectedPlot = null;
        plotRectangles.clear();
        overlayPane.getChildren().clear();
        clearPlotDetails();
        BufferedImage bi = SwingFXUtils.fromFXImage(img, null);
        currentMat = (bi != null) ? imageService.bufferedImageToMat(bi) : null;
        updateOverlays();
        statusLabel.setText("Status: Loaded " + data.getFile().getName());
        loadCropNamesForCurrentImage();
    }

    private void updateOverlays() {
        overlayPane.getChildren().clear();
        plotRectangles.clear();
        if (currentMat == null) return;

        List<PlotData> all = new ArrayList<>();
        all.addAll(detectedPlots);
        all.addAll(manualPlots);
        all.addAll(importedPlots);
        currentPlots = all;

        Image img = imageView.getImage();
        if (img == null) return;

        Rectangle2D displayRect = getImageDisplayRect();
        if (displayRect == null) return;

        double scaleX = displayRect.getWidth() / img.getWidth();
        double scaleY = displayRect.getHeight() / img.getHeight();
        double offsetX = displayRect.getMinX();
        double offsetY = displayRect.getMinY();

        for (PlotData plot : all) {
            Rect rect = plot.getRect();
            double x = rect.x * scaleX + offsetX;
            double y = rect.y * scaleY + offsetY;
            double w = rect.width * scaleX;
            double h = rect.height * scaleY;

            Rectangle overlayRect = new Rectangle(x, y, w, h);
            overlayRect.setStroke(Color.RED);
            overlayRect.setStrokeWidth(4);
            overlayRect.setStrokeType(StrokeType.OUTSIDE);
            overlayRect.setFill(Color.color(1, 0, 0, 0.01));
            overlayRect.setPickOnBounds(true);
            overlayRect.setUserData(plot.getId());

            overlayRect.setOnMouseEntered(e -> {
                if (!isAddingPlot) updateResizeCursor(overlayRect, e);
            });
            overlayRect.setOnMouseMoved(e -> {
                if (!isAddingPlot) updateResizeCursor(overlayRect, e);
            });
            overlayRect.setOnMouseExited(e -> {
                if (!isAddingPlot && resizeDirection == ResizeDirection.NONE) {
                    overlayPane.setCursor(Cursor.DEFAULT);
                }
            });
            overlayRect.setOnMousePressed(e -> {
                if (!isAddingPlot) {
                    activeRectangle = overlayRect;
                    startResize(overlayRect, e);
                    resizing = true;
                    e.consume();
                }
            });

            plotRectangles.put(plot.getId(), overlayRect);
            overlayPane.getChildren().add(overlayRect);
        }

        if (selectedPlot != null) {
            Rectangle selRect = plotRectangles.get(selectedPlot.getId());
            if (selRect != null) {
                selRect.setStroke(Color.GREEN);
            }
        }
    }

    // ============================================================
    //  RESIZE METHODS (with Undo)
    // ============================================================
    private void updateResizeCursor(Rectangle rect, MouseEvent e) {
        double x = e.getX(), y = e.getY(), w = rect.getWidth(), h = rect.getHeight(), threshold = 15;
        if (x <= threshold && y <= threshold) rect.setCursor(Cursor.NW_RESIZE);
        else if (x >= w - threshold && y <= threshold) rect.setCursor(Cursor.NE_RESIZE);
        else if (x <= threshold && y >= h - threshold) rect.setCursor(Cursor.SW_RESIZE);
        else if (x >= w - threshold && y >= h - threshold) rect.setCursor(Cursor.SE_RESIZE);
        else if (x <= threshold) rect.setCursor(Cursor.W_RESIZE);
        else if (x >= w - threshold) rect.setCursor(Cursor.E_RESIZE);
        else if (y <= threshold) rect.setCursor(Cursor.N_RESIZE);
        else if (y >= h - threshold) rect.setCursor(Cursor.S_RESIZE);
        else rect.setCursor(Cursor.DEFAULT);
    }

    private void startResize(Rectangle rect, MouseEvent e) {
        saveStateForUndo();
        Bounds sceneBounds = rect.localToScene(rect.getBoundsInLocal());
        double threshold = 8;
        boolean left = Math.abs(e.getSceneX() - sceneBounds.getMinX()) <= threshold;
        boolean right = Math.abs(e.getSceneX() - sceneBounds.getMaxX()) <= threshold;
        boolean top = Math.abs(e.getSceneY() - sceneBounds.getMinY()) <= threshold;
        boolean bottom = Math.abs(e.getSceneY() - sceneBounds.getMaxY()) <= threshold;

        if (left && top) resizeDirection = ResizeDirection.TOP_LEFT;
        else if (right && top) resizeDirection = ResizeDirection.TOP_RIGHT;
        else if (left && bottom) resizeDirection = ResizeDirection.BOTTOM_LEFT;
        else if (right && bottom) resizeDirection = ResizeDirection.BOTTOM_RIGHT;
        else if (left) resizeDirection = ResizeDirection.LEFT;
        else if (right) resizeDirection = ResizeDirection.RIGHT;
        else if (top) resizeDirection = ResizeDirection.TOP;
        else if (bottom) resizeDirection = ResizeDirection.BOTTOM;
        else { resizeDirection = ResizeDirection.NONE; return; }

        String plotId = (String) rect.getUserData();
        for (PlotData p : currentPlots) {
            if (p.getId().equals(plotId)) { resizingPlot = p; break; }
        }
        if (resizingPlot == null) { resizeDirection = ResizeDirection.NONE; return; }

        originalX1 = rect.getX(); originalY1 = rect.getY();
        originalX2 = rect.getX() + rect.getWidth(); originalY2 = rect.getY() + rect.getHeight();
        Point2D p = overlayPane.sceneToLocal(e.getSceneX(), e.getSceneY());
        resizeStartX = p.getX(); resizeStartY = p.getY();
        e.consume();
    }

    private void doResize(Rectangle rect, MouseEvent e) {
        if (resizingPlot == null || resizeDirection == ResizeDirection.NONE) return;
        Image img = imageView.getImage();
        if (img == null) return;

        Point2D overlayPoint = overlayPane.sceneToLocal(e.getSceneX(), e.getSceneY());
        double mouseX = overlayPoint.getX(), mouseY = overlayPoint.getY();
        double newX1 = originalX1, newY1 = originalY1, newX2 = originalX2, newY2 = originalY2;
        double deltaX = mouseX - resizeStartX, deltaY = mouseY - resizeStartY;

        switch (resizeDirection) {
            case LEFT: newX1 = Math.min(mouseX, originalX2 - 5); break;
            case RIGHT: newX2 = Math.max(mouseX, originalX1 + 5); break;
            case TOP: newY1 = Math.min(mouseY, originalY2 - 5); break;
            case BOTTOM: newY2 = Math.max(mouseY, originalY1 + 5); break;
            case TOP_LEFT: newX1 = Math.min(mouseX, originalX2 - 5); newY1 = Math.min(mouseY, originalY2 - 5); break;
            case TOP_RIGHT: newX2 = Math.max(mouseX, originalX1 + 5); newY1 = Math.min(mouseY, originalY2 - 5); break;
            case BOTTOM_LEFT: newX1 = Math.min(mouseX, originalX2 - 5); newY2 = Math.max(mouseY, originalY1 + 5); break;
            case BOTTOM_RIGHT: newX2 = Math.max(mouseX, originalX1 + 5); newY2 = Math.max(mouseY, originalY1 + 5); break;
            default: return;
        }

        Rectangle2D displayRect = getImageDisplayRect();
        if (displayRect == null) return;
        newX1 = Math.max(displayRect.getMinX(), Math.min(newX1, displayRect.getMaxX() - 5));
        newY1 = Math.max(displayRect.getMinY(), Math.min(newY1, displayRect.getMaxY() - 5));
        newX2 = Math.max(displayRect.getMinX() + 5, Math.min(newX2, displayRect.getMaxX()));
        newY2 = Math.max(displayRect.getMinY() + 5, Math.min(newY2, displayRect.getMaxY()));

        rect.setX(newX1); rect.setY(newY1); rect.setWidth(newX2 - newX1); rect.setHeight(newY2 - newY1);

        double scaleX = img.getWidth() / displayRect.getWidth();
        double scaleY = img.getHeight() / displayRect.getHeight();
        int imgX1 = (int) Math.round((newX1 - displayRect.getMinX()) * scaleX);
        int imgY1 = (int) Math.round((newY1 - displayRect.getMinY()) * scaleY);
        int imgX2 = (int) Math.round((newX2 - displayRect.getMinX()) * scaleX);
        int imgY2 = (int) Math.round((newY2 - displayRect.getMinY()) * scaleY);

        if (imgX1 < 0) imgX1 = 0; if (imgY1 < 0) imgY1 = 0;
        if (imgX2 > img.getWidth()) imgX2 = (int) img.getWidth();
        if (imgY2 > img.getHeight()) imgY2 = (int) img.getHeight();
        if (imgX2 < imgX1 + 2) imgX2 = imgX1 + 2;
        if (imgY2 < imgY1 + 2) imgY2 = imgY1 + 2;

        resizingPlot.setX1(imgX1); resizingPlot.setY1(imgY1);
        resizingPlot.setX2(imgX2); resizingPlot.setY2(imgY2);
        resizingPlot.updateArea();
        if (selectedPlot != null && selectedPlot.getId().equals(resizingPlot.getId())) {
            displayPlotDetails(selectedPlot);
        }
    }

    private void endResize() {
        resizeDirection = ResizeDirection.NONE;
        resizingPlot = null; activeRectangle = null; resizing = false;
        overlayPane.setCursor(Cursor.DEFAULT);
    }

    // ============================================================
    //  OVERLAY MOUSE HANDLERS (drawing + resize)
    // ============================================================
    private void onOverlayMousePressed(MouseEvent event) {
        if (isAddingPlot) {
            Rectangle2D displayRect = getImageDisplayRect();
            if (displayRect == null) return;
            double x = Math.max(displayRect.getMinX(), Math.min(event.getX(), displayRect.getMaxX()));
            double y = Math.max(displayRect.getMinY(), Math.min(event.getY(), displayRect.getMaxY()));
            currentRect = new Rectangle(x, y, 0, 0);
            currentRect.setStroke(Color.ORANGE);
            currentRect.setFill(Color.ORANGE.deriveColor(0, 1, 1, 0.3));
            currentRect.setStrokeWidth(2);
            overlayPane.getChildren().add(currentRect);
        }
    }

    private void onOverlayMouseDragged(MouseEvent event) {
        if (resizing && activeRectangle != null) {
            doResize(activeRectangle, event);
            event.consume();
            return;
        }
        if (isAddingPlot && currentRect != null) {
            Rectangle2D displayRect = getImageDisplayRect();
            if (displayRect == null) return;
            double x = Math.max(displayRect.getMinX(), Math.min(event.getX(), displayRect.getMaxX()));
            double y = Math.max(displayRect.getMinY(), Math.min(event.getY(), displayRect.getMaxY()));
            double startX = currentRect.getX(), startY = currentRect.getY();
            double minX = Math.min(startX, x), minY = Math.min(startY, y);
            double w = Math.abs(x - startX), h = Math.abs(y - startY);
            currentRect.setX(minX); currentRect.setY(minY);
            currentRect.setWidth(w); currentRect.setHeight(h);
        }
    }

    private void onOverlayMouseReleased(MouseEvent event) {
        if (resizing) {
            endResize();
            event.consume();
            return;
        }
        if (isAddingPlot && currentRect != null) {
            saveStateForUndo();
            double x = currentRect.getX(), y = currentRect.getY(), w = currentRect.getWidth(), h = currentRect.getHeight();
            overlayPane.getChildren().remove(currentRect);
            currentRect = null;
            if (w < 5 || h < 5) {
                statusLabel.setText("Status: Rectangle too small, ignored.");
                isAddingPlot = false;
                overlayPane.setCursor(Cursor.DEFAULT);
                return;
            }
            Image img = imageView.getImage();
            if (img == null) { isAddingPlot = false; overlayPane.setCursor(Cursor.DEFAULT); return; }
            Rectangle2D displayRect = getImageDisplayRect();
            if (displayRect == null) { isAddingPlot = false; overlayPane.setCursor(Cursor.DEFAULT); return; }
            double localX = x - displayRect.getMinX(), localY = y - displayRect.getMinY();
            double localW = w, localH = h;
            localX = Math.max(0, Math.min(localX, displayRect.getWidth()));
            localY = Math.max(0, Math.min(localY, displayRect.getHeight()));
            localW = Math.min(localW, displayRect.getWidth() - localX);
            localH = Math.min(localH, displayRect.getHeight() - localY);
            if (localW < 1 || localH < 1) {
                statusLabel.setText("Status: Cropped region out of image bounds.");
                isAddingPlot = false;
                overlayPane.setCursor(Cursor.DEFAULT);
                return;
            }
            double scaleX = img.getWidth() / displayRect.getWidth();
            double scaleY = img.getHeight() / displayRect.getHeight();
            int origX = (int) Math.round(localX * scaleX);
            int origY = (int) Math.round(localY * scaleY);
            int origW = (int) Math.round(localW * scaleX);
            int origH = (int) Math.round(localH * scaleY);
            if (origX < 0) origX = 0; if (origY < 0) origY = 0;
            if (origX + origW > img.getWidth()) origW = (int) img.getWidth() - origX;
            if (origY + origH > img.getHeight()) origH = (int) img.getHeight() - origY;
            if (origW < 1 || origH < 1) {
                statusLabel.setText("Status: Cropped region out of image bounds.");
                isAddingPlot = false;
                overlayPane.setCursor(Cursor.DEFAULT);
                return;
            }
            int x2 = origX + origW, y2 = origY + origH;
            String baseName = "MANUAL";
            if (currentImageName != null && !currentImageName.isEmpty()) {
                baseName = currentImageName.replaceAll("\\.[^.]+$", "");
            }
            int nextId = manualPlots.size() + 1;
            String id = baseName + "_M" + nextId;
            String cropName = cropNameField.getSelectionModel().getSelectedItem();
            String cropType = cropTypeCombo.getSelectionModel().getSelectedItem();
            String nitrogen = nitrogenCombo.getSelectionModel().getSelectedItem();
            double biomass = 0.0;
            try { String biomassText = biomassField.getText().trim(); if (!biomassText.isEmpty()) biomass = Double.parseDouble(biomassText); } catch (NumberFormatException e) { /* ignore */ }
            PlotData newPlot = new PlotData(id, origX, origY, x2, y2, origW * origH,
                    cropName, cropType, nitrogen, biomass);
            manualPlots.add(newPlot);
            selectedPlot = newPlot;
            updateOverlays();
            displayPlotDetails(newPlot);
            statusLabel.setText("Status: Added manual Plot " + id + " (not saved yet). Click 'Save' to store.");
            isAddingPlot = false;
            overlayPane.setCursor(Cursor.DEFAULT);
        }
    }

    private void onOverlayMouseClicked(MouseEvent event) {
        if (!isAddingPlot && !resizing) {
            onImageViewClicked(event);
        }
    }

    // ============================================================
    //  CLICK DETECTION – INSTANT COLOR CHANGE
    // ============================================================
    @FXML
    private void onImageViewClicked(MouseEvent event) {
        if (currentPlots.isEmpty()) return;
        Image img = imageView.getImage();
        if (img == null) return;
        Rectangle2D displayRect = getImageDisplayRect();
        if (displayRect == null) return;
        double mouseX = event.getX(), mouseY = event.getY();
        double x = Math.max(0, Math.min(mouseX - displayRect.getMinX(), displayRect.getWidth()));
        double y = Math.max(0, Math.min(mouseY - displayRect.getMinY(), displayRect.getHeight()));
        double scaleX = img.getWidth() / displayRect.getWidth();
        double scaleY = img.getHeight() / displayRect.getHeight();
        int origX = (int) (x * scaleX);
        int origY = (int) (y * scaleY);
        PlotData foundPlot = null;
        for (PlotData plot : currentPlots) {
            Rect rect = plot.getRect();
            if (origX >= rect.x && origX <= rect.x + rect.width &&
                origY >= rect.y && origY <= rect.y + rect.height) {
                foundPlot = plot;
                break;
            }
        }
        for (Rectangle r : plotRectangles.values()) { r.setStroke(Color.RED); }
        if (foundPlot != null) {
            selectedPlot = foundPlot;
            Rectangle selRect = plotRectangles.get(foundPlot.getId());
            if (selRect != null) selRect.setStroke(Color.GREEN);
            displayPlotDetails(foundPlot);
            statusLabel.setText("Status: Selected Plot " + foundPlot.getId());
        } else {
            selectedPlot = null;
            clearPlotDetails();
            statusLabel.setText("Status: Clicked outside any plot");
        }
    }

    // ============================================================
    //  MANUAL PLOT ADDITION (Toggle)
    // ============================================================
    @FXML
    public void onAddPlot(ActionEvent event) {
        if (imageView.getImage() == null) {
            statusLabel.setText("Status: Please load an image first.");
            return;
        }
        isAddingPlot = !isAddingPlot;
        if (isAddingPlot) {
            statusLabel.setText("Status: Click and drag on the image to define a plot.");
            overlayPane.setCursor(Cursor.CROSSHAIR);
        } else {
            statusLabel.setText("Status: Add mode disabled.");
            overlayPane.setCursor(Cursor.DEFAULT);
            if (currentRect != null) {
                overlayPane.getChildren().remove(currentRect);
                currentRect = null;
            }
        }
    }

    // ============================================================
    //  SAVE PLOT DATA HELPER (Crop Image + DB)
    // ============================================================
    private void savePlotData(PlotData plot) {
        if (plot == null) return;
        String cropType = plot.getCropType();
        String cropName = plot.getCropName();
        if (cropType == null || cropType.trim().isEmpty()) cropType = "Unknown";
        if (cropName == null || cropName.trim().isEmpty()) cropName = "Unnamed";
        String harvestName = getHarvestName();
        File plotsFolder = new File("plots");
        if (!plotsFolder.exists()) plotsFolder.mkdirs();
        File harvestFolder = new File(plotsFolder, harvestName);
        if (!harvestFolder.exists()) harvestFolder.mkdirs();
        File cropFolder = new File(harvestFolder, cropType);
        if (!cropFolder.exists()) cropFolder.mkdirs();
        String safeCropName = cropName.replaceAll("[\\\\/:*?\"<>|]", "_");
        File imageFile = new File(cropFolder, safeCropName + ".png");
        int count = 1;
        while (imageFile.exists()) {
            imageFile = new File(cropFolder, safeCropName + "_" + count + ".png");
            count++;
        }
        String imagePath = null;
        if (currentMat != null) {
            Rect rect = plot.getRect();
            if (rect.x >= 0 && rect.y >= 0 &&
                rect.x + rect.width <= currentMat.cols() &&
                rect.y + rect.height <= currentMat.rows()) {
                Mat cropped = new Mat(currentMat, rect);
                imagePath = imageFile.getAbsolutePath();
                Imgcodecs.imwrite(imagePath, cropped);
                plot.setImagePath(imagePath);
            } else {
                statusLabel.setText("Status: Plot coordinates out of bounds.");
            }
        }
        dbService.savePlot(plot, imagePath);
    }

    // ============================================================
    //  TOAST / SAVED INDICATOR
    // ============================================================
    private void showSavedToast(String message) {
        Label toast = new Label(message);
        toast.setStyle("-fx-background-color: rgba(76, 175, 80, 0.9); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 5;");
        toast.layoutXProperty().bind(overlayPane.widthProperty().subtract(toast.widthProperty()).divide(2));
        overlayPane.getChildren().add(toast);
        FadeTransition fade = new FadeTransition(Duration.seconds(1), toast);
        fade.setDelay(Duration.seconds(1));
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setOnFinished(e -> overlayPane.getChildren().remove(toast));
        fade.play();
    }

    // ============================================================
    //  UPDATE, SAVE, DELETE (with Undo)
    // ============================================================
    @FXML
    public void onUpdatePlot(ActionEvent event) {
        saveStateForUndo();
        if (selectedPlot == null) {
            statusLabel.setText("Status: No plot selected.");
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No plot selected");
            alert.setContentText("Please click on a plot rectangle first.");
            alert.showAndWait();
            return;
        }
        String cropName = cropNameField.getSelectionModel().getSelectedItem();
        String cropType = cropTypeCombo.getSelectionModel().getSelectedItem();
        String nitrogen = nitrogenCombo.getSelectionModel().getSelectedItem();
        double biomass = 0.0;
        try {
            String biomassText = biomassField.getText().trim();
            if (!biomassText.isEmpty()) biomass = Double.parseDouble(biomassText);
        } catch (NumberFormatException e) {
            statusLabel.setText("Status: Invalid biomass value.");
            Alert alert = new Alert(Alert.AlertType.ERROR, "Biomass must be a number.");
            alert.showAndWait();
            return;
        }
        selectedPlot.setCropName(cropName);
        selectedPlot.setCropType(cropType);
        selectedPlot.setNitrogenLevel(nitrogen);
        selectedPlot.setBiomass(biomass);
        displayPlotDetails(selectedPlot);
        statusLabel.setText("Status: Plot " + selectedPlot.getId() + " updated (in memory).");
    }

    @FXML
    public void onSavePlot(ActionEvent event) {
        saveStateForUndo();
        if (selectedPlot == null) {
            statusLabel.setText("Status: No plot selected.");
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No plot selected");
            alert.setContentText("Please click on a plot rectangle first.");
            alert.showAndWait();
            return;
        }
        String cropName = cropNameField.getSelectionModel().getSelectedItem();
        String cropType = cropTypeCombo.getSelectionModel().getSelectedItem();
        String nitrogen = nitrogenCombo.getSelectionModel().getSelectedItem();
        double biomass = 0.0;
        try {
            String biomassText = biomassField.getText().trim();
            if (!biomassText.isEmpty()) biomass = Double.parseDouble(biomassText);
        } catch (NumberFormatException e) {
            statusLabel.setText("Status: Invalid biomass.");
            Alert alert = new Alert(Alert.AlertType.ERROR, "Biomass must be a number.");
            alert.showAndWait();
            return;
        }
        selectedPlot.setCropName(cropName);
        selectedPlot.setCropType(cropType);
        selectedPlot.setNitrogenLevel(nitrogen);
        selectedPlot.setBiomass(biomass);
        savePlotData(selectedPlot);
        statusLabel.setText("Status: Plot " + selectedPlot.getId() + " saved (image & DB).");
        showSavedToast("✅ Saved: " + selectedPlot.getId());
    }

    @FXML
    public void onDeletePlot(ActionEvent event) {
        saveStateForUndo();
        if (selectedPlot == null) {
            statusLabel.setText("Status: No plot selected.");
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No plot selected");
            alert.setContentText("Please click on a plot rectangle first.");
            alert.showAndWait();
            return;
        }
        String imagePath = selectedPlot.getImagePath();
        if (imagePath != null && !imagePath.isEmpty()) {
            File f = new File(imagePath);
            if (f.exists()) f.delete();
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Plot " + selectedPlot.getId() + "?");
        confirm.setContentText(
                "This will permanently delete the plot from the database and remove it from the list.\n\n"
                + "Crop: " + (selectedPlot.getCropName() != null ? selectedPlot.getCropName() : "Unnamed")
                + "\nCoordinates: (" + selectedPlot.getX1() + "," + selectedPlot.getY1()
                + ") - (" + selectedPlot.getX2() + "," + selectedPlot.getY2() + ")"
        );
        ButtonType deleteButton = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(deleteButton, cancelButton);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == deleteButton) {
            String deletedPlotId = selectedPlot.getId();
            dbService.deletePlot(deletedPlotId);
            manualPlots.remove(selectedPlot);
            importedPlots.remove(selectedPlot);
            detectedPlots.remove(selectedPlot);
            if (currentPlots != null) { currentPlots.remove(selectedPlot); }
            plotRectangles.remove(deletedPlotId);
            selectedPlot = null;
            clearPlotDetails();
            updateOverlays();
            statusLabel.setText("Status: Plot " + deletedPlotId + " deleted successfully.");
        } else {
            statusLabel.setText("Status: Deletion cancelled.");
        }
    }

    // ============================================================
    //  DISPLAY / CLEAR DETAILS
    // ============================================================
    private void displayPlotDetails(PlotData plot) {
        selectedPlot = plot;
        plotIdLabel.setText(plot.getId());
        plotX1Label.setText(String.valueOf(plot.getX1()));
        plotY1Label.setText(String.valueOf(plot.getY1()));
        plotX2Label.setText(String.valueOf(plot.getX2()));
        plotY2Label.setText(String.valueOf(plot.getY2()));
        plotWidthLabel.setText(String.valueOf(plot.getWidth()));
        plotHeightLabel.setText(String.valueOf(plot.getHeight()));
        plotAreaLabel.setText(String.format("%.2f", plot.getArea()));
        cropNameField.getSelectionModel().select(plot.getCropName());
        cropTypeCombo.getSelectionModel().select(plot.getCropType());
        nitrogenCombo.getSelectionModel().select(plot.getNitrogenLevel());
        biomassField.setText(plot.getBiomass() > 0 ? String.valueOf(plot.getBiomass()) : "");
        String harvest = getHarvestName();
        String cropCode = getCropCodeForPlot(plot);
        fillBiomass(harvest, cropCode);
    }

    private void clearPlotDetails() {
        selectedPlot = null;
        plotIdLabel.setText("");
        plotX1Label.setText("");
        plotY1Label.setText("");
        plotX2Label.setText("");
        plotY2Label.setText("");
        plotWidthLabel.setText("");
        plotHeightLabel.setText("");
        plotAreaLabel.setText("");
        cropNameField.getSelectionModel().clearSelection();
        cropTypeCombo.getSelectionModel().clearSelection();
        nitrogenCombo.getSelectionModel().clearSelection();
        biomassField.clear();
        excelBiomassLabel.setText("-");
    }

    // ============================================================
    //  UNDO / REDO CORE
    // ============================================================
    private AppState saveState() {
        AppState state = new AppState();
        state.detectedPlots = detectedPlots.stream().map(PlotData::copy).collect(Collectors.toList());
        state.manualPlots = manualPlots.stream().map(PlotData::copy).collect(Collectors.toList());
        state.importedPlots = importedPlots.stream().map(PlotData::copy).collect(Collectors.toList());
        state.currentPlots = currentPlots.stream().map(PlotData::copy).collect(Collectors.toList());
        state.selectedPlot = selectedPlot != null ? selectedPlot.copy() : null;
        state.importedPlotsMap = new HashMap<>();
        for (Map.Entry<String, List<PlotData>> entry : importedPlotsMap.entrySet()) {
            state.importedPlotsMap.put(entry.getKey(),
                    entry.getValue().stream().map(PlotData::copy).collect(Collectors.toList()));
        }
        return state;
    }

    private void restoreState(AppState state) {
        detectedPlots = state.detectedPlots;
        manualPlots = state.manualPlots;
        importedPlots = state.importedPlots;
        currentPlots = state.currentPlots;
        selectedPlot = state.selectedPlot;
        importedPlotsMap = state.importedPlotsMap;
        updateOverlays();
        if (selectedPlot != null) displayPlotDetails(selectedPlot);
        else clearPlotDetails();
        statusLabel.setText("State restored.");
    }

    private void saveStateForUndo() {
        if (isUndoRedoInProgress) return;
        undoStack.push(saveState());
        redoStack.clear();
    }

    private void synchronizeSavedPlots(List<PlotData> oldPlots, List<PlotData> newPlots) {
        Map<String, PlotData> oldSaved = oldPlots.stream()
                .filter(p -> p.getImagePath() != null && !p.getImagePath().isEmpty())
                .collect(Collectors.toMap(PlotData::getId, p -> p));
        Map<String, PlotData> newSaved = newPlots.stream()
                .filter(p -> p.getImagePath() != null && !p.getImagePath().isEmpty())
                .collect(Collectors.toMap(PlotData::getId, p -> p));

        for (Map.Entry<String, PlotData> entry : oldSaved.entrySet()) {
            String id = entry.getKey();
            PlotData oldPlot = entry.getValue();
            PlotData newPlot = newSaved.get(id);
            if (newPlot == null || !newPlot.getImagePath().equals(oldPlot.getImagePath())) {
                dbService.deletePlot(id);
                File f = new File(oldPlot.getImagePath());
                if (f.exists()) f.delete();
            }
        }
        for (Map.Entry<String, PlotData> entry : newSaved.entrySet()) {
            String id = entry.getKey();
            PlotData newPlot = entry.getValue();
            PlotData oldPlot = oldSaved.get(id);
            if (oldPlot == null || !oldPlot.getImagePath().equals(newPlot.getImagePath())) {
                savePlotData(newPlot);
            }
        }
    }

    @FXML
    public void onUndo(ActionEvent event) {
        if (undoStack.isEmpty()) { statusLabel.setText("Nothing to undo."); return; }
        isUndoRedoInProgress = true;
        AppState current = saveState();
        redoStack.push(current);
        AppState previous = undoStack.pop();
        synchronizeSavedPlots(current.currentPlots, previous.currentPlots);
        restoreState(previous);
        isUndoRedoInProgress = false;
    }

    @FXML
    public void onRedo(ActionEvent event) {
        if (redoStack.isEmpty()) { statusLabel.setText("Nothing to redo."); return; }
        isUndoRedoInProgress = true;
        AppState current = saveState();
        undoStack.push(current);
        AppState next = redoStack.pop();
        synchronizeSavedPlots(current.currentPlots, next.currentPlots);
        restoreState(next);
        isUndoRedoInProgress = false;
    }

    // ============================================================
    //  HELPER
    // ============================================================
    private Rectangle2D getImageDisplayRect() {
        Image img = imageView.getImage();
        if (img == null) return null;
        double imgW = img.getWidth(), imgH = img.getHeight();
        double viewW = imageView.getFitWidth(), viewH = imageView.getFitHeight();
        if (viewW <= 0) viewW = imageView.getLayoutBounds().getWidth();
        if (viewH <= 0) viewH = imageView.getLayoutBounds().getHeight();
        if (viewW <= 0 || viewH <= 0) return null;
        double scale = Math.min(viewW / imgW, viewH / imgH);
        double dispW = imgW * scale, dispH = imgH * scale;
        double offsetX = (viewW - dispW) / 2, offsetY = (viewH - dispH) / 2;
        return new Rectangle2D(offsetX, offsetY, dispW, dispH);
    }

    private Stage getStage() {
        return (Stage) imageView.getScene().getWindow();
    }
}