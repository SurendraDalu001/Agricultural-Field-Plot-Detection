package com.plotdetection.service;

import com.plotdetection.model.CropInfo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.*;
import java.util.*;


public class CropDataService {
	private boolean loaded = false;
    // Harvest -> ImageID -> List<CropInfo>
    private final Map<String, Map<String, List<CropInfo>>> cropData = new HashMap<>();

    public void loadCropData(File file) throws IOException {
        cropData.clear();

        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".csv")) {
            loadCSV(file);
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            loadExcel(file);
        } else {
            throw new IOException("Unsupported file format. Use CSV or Excel (.xls, .xlsx)");
        }
    }

    private void loadCSV(File file) throws IOException {
        String defaultHarvest = "Default Harvest";
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(",");
                if (cols.length < 2) continue;
                String imageId = cols[0].trim();
                List<CropInfo> crops = new ArrayList<>();
                for (int i = 1; i < cols.length; i++) {
                    String crop = cols[i].trim();
                    if (!crop.isEmpty()) {
                        crops.add(parseCrop(crop));
                    }
                }
                if (!crops.isEmpty()) {
                    cropData.computeIfAbsent(defaultHarvest, k -> new HashMap<>())
                            .put(imageId, crops);
                }
            }
        }
    }

    private void loadExcel(File file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String harvestName = sheet.getSheetName().trim();

                for (Row row : sheet) {
                    if (row == null) continue;
                    Cell firstCell = row.getCell(0);
                    if (firstCell == null) continue;
                    String firstCellValue = getCellString(firstCell);
                    if (firstCellValue == null || firstCellValue.trim().isEmpty()) continue;

                    // Skip header rows
                    if (firstCellValue.matches("(?i).*nitrogen.*") || firstCellValue.equalsIgnoreCase("Image ID")) {
                        continue;
                    }

                    if (firstCellValue.matches("\\d+")) {
                        String imageId = firstCellValue.trim();
                        List<CropInfo> crops = new ArrayList<>();
                        for (int c = 1; c < row.getLastCellNum(); c++) {
                            Cell cell = row.getCell(c);
                            if (cell == null) continue;
                            String crop = getCellString(cell);
                            if (crop == null || crop.trim().isEmpty()) continue;
                            crops.add(parseCrop(crop.trim()));
                        }
                        if (!crops.isEmpty()) {
                            cropData.computeIfAbsent(harvestName, k -> new HashMap<>())
                                    .put(imageId, crops);
                        }
                    }
                }
            }
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((int) cell.getNumericCellValue());
            default: return null;
        }
    }

//    private CropInfo parseCrop(String cropName) {
//        String cropType = "";
//        String nitrogen = "Standard";
//        String cropCode = "";
//
//        String[] parts = cropName.split("_");
//
//        if (parts.length >= 1) {
//            cropType = capitalize(parts[0]);
//        }
//
//        if (parts.length >= 2) {
//            switch (parts[1].toLowerCase()) {
//                case "low":  nitrogen = "Low"; break;
//                case "no":   nitrogen = "No"; break;
//                default:     nitrogen = "Standard";
//            }
//        }
//
//        if (parts.length >= 3) {
//            cropCode = parts[2];
//        }
//
//        return new CropInfo(cropName, cropType, nitrogen, cropCode);
//    }
    private CropInfo parseCrop(String cropName) {
        String cropType = "";
        String nitrogen = "Standard";
        String cropCode = "";

        String[] parts = cropName.split("_");

        if (parts.length >= 1) {
            cropType = capitalize(parts[0]);
        }

        if (parts.length == 3) {
            // Examples:
            // sorghum_no_1001
            // maize_low_4501

            switch (parts[1].toLowerCase()) {
                case "low":
                    nitrogen = "Low";
                    break;
                case "no":
                    nitrogen = "No";
                    break;
                default:
                    nitrogen = "Standard";
            }

            cropCode = parts[2];

        } else if (parts.length == 2) {
            // Examples:
            // sorghum_1508
            // maize_4505
            // pearl millet_2501

            cropCode = parts[1];
            nitrogen = "Standard";
        }

        return new CropInfo(cropName, cropType, nitrogen, cropCode);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    // Primary method: get crops by harvest and imageId
    public List<CropInfo> getCrops(String harvest, String imageId) {

        Map<String, List<CropInfo>> harvestMap = cropData.get(harvest);

        if (harvestMap == null) {
            return Collections.emptyList();
        }

        return harvestMap.getOrDefault(imageId, Collections.emptyList());
    }

    // Fallback: scan all harvests
    public List<CropInfo> getCrops(String imageId) {
        for (Map<String, List<CropInfo>> harvestMap : cropData.values()) {
            if (harvestMap.containsKey(imageId)) {
                return harvestMap.get(imageId);
            }
        }
        return Collections.emptyList();
    }

    public CropInfo getCrop(String harvest, String imageId, String cropName) {
        List<CropInfo> list = getCrops(harvest, imageId);
        for (CropInfo crop : list) {
            if (crop.getCropName().equalsIgnoreCase(cropName)) {
                return crop;
            }
        }
        return null;
    }

    public void clear() {
        cropData.clear();
        loaded = false;
    }
}