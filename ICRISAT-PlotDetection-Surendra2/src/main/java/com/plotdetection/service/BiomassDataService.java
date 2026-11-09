package com.plotdetection.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class BiomassDataService {

    // Harvest -> CropID -> Biomass
    private final Map<String, Map<String, Double>> biomassData = new HashMap<>();

    public void loadBiomassData(File file) throws IOException {
        biomassData.clear();
        

        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".csv")) {
            loadCSV(file);
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            loadExcel(file);
        } else {
            throw new IOException("Unsupported file format. Use CSV or Excel (.xls, .xlsx)");
        }
    }
    public void clear() {
        biomassData.clear();
    }

    private void loadCSV(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(",");
                if (cols.length < 3) continue;
                parseRow(cols[0].trim(), cols[2].trim());
            }
        }
    }

    private void loadExcel(File file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isFirstRow = true;
            for (Row row : sheet) {
                if (isFirstRow) {
                    isFirstRow = false;
                    continue; // Skip header
                }
                if (row.getCell(0) == null) continue;
                String imagePath = row.getCell(0).getStringCellValue().trim();
                Cell biomassCell = row.getCell(2);
                if (biomassCell == null) continue;
                double biomass = biomassCell.getNumericCellValue();
                parseRow(imagePath, String.valueOf(biomass));
            }
        }
    }

    private void parseRow(String imagePath, String biomassStr) {
        try {
            double biomass = Double.parseDouble(biomassStr);
            String harvest = extractHarvest(imagePath);
            String cropId = extractCropId(imagePath);
            if (!cropId.isEmpty()) {
                biomassData
                        .computeIfAbsent(harvest, k -> new HashMap<>())
                        .put(cropId, biomass);
            }
        } catch (NumberFormatException e) {
            // skip malformed row
        }
    }

    public Double getBiomass(String harvest, String cropId) {
        Map<String, Double> map = biomassData.get(harvest);
        return map != null ? map.get(cropId) : null;
    }

    private String extractHarvest(String path) {
        path = path.replace("\\", "/");
        String[] parts = path.split("/");
        for (String p : parts) {
            if (p.matches("(?i)Harvest\\s*\\d+")) {
                return p;
            }
        }
        return "Unknown Harvest";
    }

    private String extractCropId(String path) {
        String fileName = new File(path).getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            fileName = fileName.substring(0, dotIndex);
        }
        int index = fileName.lastIndexOf('_');
        if (index == -1) return "";
        return fileName.substring(index + 1);
    }
}