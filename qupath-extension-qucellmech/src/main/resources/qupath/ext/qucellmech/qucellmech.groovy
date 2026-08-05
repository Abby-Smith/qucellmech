/* Last tested on QuPath-0.5.1
 * 
 * This scripts requires qupath-extension-cellpose 
 * cf https://github.com/BIOP/qupath-extension-cellpose
 */


// ── Imports ───────────────────────────────────────────────────────────────────
import static qupath.lib.scripting.QP.*
import qupath.lib.regions.ImagePlane

import qupath.ext.biop.cellpose.Cellpose2D
import qupath.lib.analysis.features.ObjectMeasurements
import qupath.opencv.ops.ImageOps

import qupath.lib.objects.PathObject
import qupath.lib.projects.Projects
import qupath.lib.io.GsonTools
import com.google.gson.JsonParser

import qupath.lib.roi.ROIs
import qupath.lib.objects.PathObjects
import qupath.lib.roi.GeometryTools
import org.locationtech.jts.geom.Geometry

// =================================================
// ============= STEP 0: PROJECT SETUP =============
// =================================================

def project = getProject()
if (project == null || project.getPath() == null) {
    println("Open a project before running this script!")
    return
}

// GeoJSON folder
def gjFolder = new File(gjDir)
if (runCellpose) {
    if (!gjFolder.exists()) {gjFolder.mkdirs()}
} else {
    if (!gjFolder.exists()) {
        print("ERROR: GeoJSON folder does not exist: " + gjDir)
        return
    }
}

// Make a temporary GeoJSON directory for pycellmech to run on only selected files, instead of 
// all GEOJSON files that have ever been output by cellpose/that exist in the user-defined folder.
def tempGjDir = buildFilePath(PROJECT_BASE_DIR, 'output/geojson_temp/')
def tempGjFolder = new File(tempGjDir)
if (!tempGjFolder.exists()) tempGjFolder.mkdirs()

        
// Define the csv output path and create it if not yet existant
def csvFullDir = buildFilePath(PROJECT_BASE_DIR, 'output/csv/') // for all features
def csvFullFolder = new File(csvFullDir)
if (!csvFullFolder.exists()) {
        csvFullFolder.mkdirs()
        }

def csvCleanDir = buildFilePath(PROJECT_BASE_DIR, 'output/csv_clean/') // for scalar values, to be displayed in Qupath
def csvCleanFolder = new File(csvCleanDir)
if (!csvCleanFolder.exists()) {
        csvCleanFolder.mkdirs()
        }

// =====================================================
// =============== STEP 1: CELLPOSE ==================
// =====================================================

if (runCellpose) {
    // Loop through all selected image entries in the project
    for (entry in project.getImageList().findAll { selectedImageNames.contains(it.getImageName()) }) {
        // Open each image
        def imageData = entry.readImageData()

        def imgHi = imageData.getHierarchy()
        println(imgHi)

        // Get the image name (without the extension)
        def imageName = entry.getImageName().replaceFirst(/\.[^.]+/, "")
        println(imageName)

        def server = imageData.getServer()
        println(server)
        def cal = server.getPixelCalibration()
        def downsample = 1.0

        // delete any pre-existing objects (annotations/detections) and select a full image annotation
        imgHi.removeObjects(imgHi.getAnnotationObjects(), false)   // false = remove all nested objects too
        imgHi.removeObjects(imgHi.getDetectionObjects(), false)
        
        def fullAnnotation = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getDefaultPlane()))
        imgHi.addObject(fullAnnotation)

        // ── Nuclei detection ──────────────────────────────────────────────────────────
        def stains = imageData.getColorDeconvolutionStains()
        println(stains)

        // Create a Cellpose detector for nuclei
        def cellpose_nuc = Cellpose2D.builder('nuclei')
                .preprocess(
                    ImageOps.Channels.deconvolve(stains),
                    ImageOps.Channels.extract(0)   // Channel 0 = Hematoxylin
                )
                .pixelSize(0.3)
                .diameter(10)
                .build()
        
        // this is where we actually run cellpose!
        cellpose_nuc.detectObjects(imageData, [fullAnnotation])

        def nucs = imgHi.getDetectionObjects()
        nucs.each { it.setPathClass(getPathClass("Nucleus")) }

        // ── Remove small and non-polygon (i.e. full-image rectangle) detections ──────────────────────────────────
        def minAreaMicrons = 3.0   // adjust this threshold (µm²) as needed

        def toDelete = imgHi.getDetectionObjects().findAll { det ->
            def roi      = det.getROI()
            def tooSmall = roi.getScaledArea(
                            cal.getPixelWidthMicrons(),    // use cal instead of hardcoded values
                            cal.getPixelHeightMicrons()
                        ) < minAreaMicrons
            def notPolygon = !(roi instanceof qupath.lib.roi.PolygonROI)  // proper type check

            return tooSmall || notPolygon    // OR: delete if EITHER condition is true
        }

        println("Removing ${toDelete.size()} detections (too small or non-polygon)")
        imgHi.removeObjects(toDelete, true)    // true = promote any remaining children rather than deleting them
        imgHi.removeObject(fullAnnotation, true)

        // save everything
        imgHi.fireHierarchyChangedEvent(this)
        entry.saveImageData(imageData)
        
        // ---------------
        // SAVE TO GEOJSON
        // ---------------

        // Below, we create GEOJSONs for the first time and export them to the empty GeoJSON folder.
        // We also put them in a temp folder that can be accessed by pycellmech later.


        // define geoJSON file path
        def gjPath = buildFilePath(gjDir, imageName + ".geojson")
        def tempPath = buildFilePath(tempGjDir, imageName + ".geojson")

        // Get the list of cell detections from the image
        def detections = imageData.getHierarchy().getDetectionObjects()
        
        if (!detections.isEmpty()) {
            // Write detections to GeoJSON
            exportObjectsToGeoJson(detections, gjPath, "FEATURE_COLLECTION")
            exportObjectsToGeoJson(detections, tempPath, "FEATURE_COLLECTION")

            print("Exported detections for: " + imageName + " to " + gjPath)
            // clear data so it doesn't clog up QuPath memory
            imageData.getServer().close()
            imageData = null
            
            print('Cellpose done! ' + imageName)
            System.gc()
            Thread.sleep(1500)
        } else {
            print("No detections found for: " + imageName)
        }
    }
}

// ===================================================================
// ===== STEP 2: IF NO CELLPOSE, IMPORT PRE-EXISTING GEOJSON =========
// ===================================================================

if (!runCellpose) {

    for (entry in project.getImageList().findAll { selectedImageNames.contains(it.getImageName()) }) {
        def imageData = entry.readImageData()
        def imageName = entry.getImageName().replaceFirst(/\.[^.]+/, "")
        def hierarchy = imageData.getHierarchy()

        // GEOJSON FILES MUST START WITH THE SAME NAME AS THEIR CORRESPONDING IMAGE
        def gjFile = new File(gjDir).listFiles()?.find {
            it.name.startsWith(imageName) && it.name.endsWith(".geojson")
        }
        if (gjFile == null) {
            print("No GeoJSON found for: " + imageName + " in " + gjDir)
            imageData.getServer().close(); imageData = null
            continue
        }
        print("Importing GeoJSON for: " + imageName + " from " + gjFile.absolutePath)
        hierarchy.clearAll()

        // Parse GEOJSON imports
        def jsonObject
        try {
            jsonObject = JsonParser.parseString(gjFile.text).getAsJsonObject()
        } catch (Exception e) {
            print("ERROR: could not parse ${gjFile.name}: ${e.message}")
            imageData.getServer().close(); imageData = null
            continue
        }

        def featuresArray = jsonObject.getAsJsonArray("features")
        if (featuresArray == null) {
            print("WARNING: no 'features' array in ${gjFile.name}")
            imageData.getServer().close(); imageData = null
            continue
        }

        def gsonGeom = GsonTools.getInstance()
        def defaultPathClass = getPathClass("Nucleus")
        def objects = []
        def skipped = 0

        // Modify structure of imported GEOJSON to be consistent with Pycellmech
        featuresArray.each { featureEl ->
            try {
                def featureObj = featureEl.getAsJsonObject()

                // Geometry: standard GeoJSON, safe to trust Gson's own adapter for it.
                def geomJson = featureObj.getAsJsonObject("geometry")
                if (geomJson == null) { skipped++; return }
                def geometry = gsonGeom.fromJson(geomJson, Geometry.class)
                if (geometry == null) { skipped++; return }
                def roi = GeometryTools.geometryToROI(geometry, ImagePlane.getDefaultPlane())

                // Classification: read defensively - only need the name, nothing else.
                def props = featureObj.has("properties") ? featureObj.getAsJsonObject("properties") : null
                def className = null
                if (props?.has("classification")) {
                    def c = props.getAsJsonObject("classification")
                    if (c?.has("name")) className = c.get("name").getAsString()
                }
                def pathClass = className ? getPathClass(className) : defaultPathClass

                // This pipeline treats every imported object as a detection (nucleus-level
                // segmentation), regardless of whether the source file called it an
                // "annotation" or "detection" - see note below.
                objects << PathObjects.createDetectionObject(roi, pathClass)

            } catch (Exception e) {
                skipped++
                print("WARNING: skipping malformed feature in ${gjFile.name}: ${e.message}")
            }
        }

        hierarchy.addObjects(objects)
        hierarchy.fireHierarchyChangedEvent(this)
        entry.saveImageData(imageData)

        def detections = imageData.getHierarchy().getDetectionObjects()

        // save reformatted geojson will be saved to the tempDir for pycellmech to run on

        if (!detections.isEmpty()) {
            def tempPath = buildFilePath(tempGjDir, imageName + ".geojson")
            exportObjectsToGeoJson(detections, tempPath, "FEATURE_COLLECTION")
            print("Normalized GeoJSON ready for pycellmech: " + tempPath)
        } else {
            print("No detections to export for: " + imageName)
        }

        imageData.getServer().close(); imageData = null
        System.gc()

        print("Imported ${objects.size()} objects (${skipped} skipped) for: ${imageName}")
    }
}

// ===================================================
// ============== STEP 3: PYCELLMECH =================
// ===================================================

// Run pycellmech on the temp folder instead of the full gjDir. Create a command that will
// call pycellmech into QuPath from a user-defined pycellmech executable path.

def pythonExePath = pycellmechExePath.replace("/bin/pycellmech", "/bin/python3")
def command = [pythonExePath, pycellmechExePath, "--geojson_folder", tempGjDir, "--csv_save", csvFullDir]
def outprint = new StringBuffer()
def error = new StringBuffer()
def process = command.execute()

process.consumeProcessOutput(outprint, error)
process.waitFor()
print("pycellmech output message: " + outprint)
print("pycellmech error: " + error)

process.waitFor() // make sure processing is complete before moving on

// Clean up the temp folder
tempGjFolder.listFiles()?.each { it.delete() }
tempGjFolder.delete()
print("Wiped temp GeoJSON folder")


// =======================================================================
// ===== STEP 4: DISPLAY PYCELLMECH RESULTS IN QUPATH DETECTIONS =========
// =======================================================================

// Finally, we want to use this command to process the geojson files and display their pycellmech measurements in QuPath

// Helper: normalize a header name for flexible matching (strips underscores/spaces/hyphens, lowercases)
// e.g. "object_ID", "objectID", "Object-Id" -> "objectid"; "ID", "id" -> "id"

def normalizeHeader = { String h -> h.toLowerCase().replaceAll(/[^a-z0-9]/, "") }

for (entry in project.getImageList().findAll { selectedImageNames.contains(it.getImageName()) }) {
    // create CSV file with only scalar-value features, corresponding to GeoJSON file (imageName)
    def imageData = entry.readImageData()
    def imageName = entry.getImageName().replaceFirst(/\.[^.]+/, "")
    def csvFile = new File(csvCleanDir).listFiles()?.find {
        it.name.startsWith(imageName) && it.name.endsWith("_features.csv")
    }
    if (csvFile == null) {
        print("No CSV found for: " + imageName)
        continue
    }

    def detections = imageData.getHierarchy().getDetectionObjects()
    // define image hierarchy (to be used later when displaying feature calculations)
    def hierarchy = imageData.getHierarchy()
    def headers = null
    def idColumnIndex = -1

    // debug
    print("Reading CSV: " + csvFile.absolutePath)
    print("Number of detections found: " + detections.size())

    csvFile.eachLine { line, lineNumber ->
        // first line in CSV is the header, all subsequent lines have an object ID that we can access by index
        if (lineNumber == 1) {
            headers = line.split(",")
            println(headers)

            // Find the ID column, tolerating variations like object_id, object_ID,
            // objectID, id, ID, Object-Id, etc.
            idColumnIndex = headers.findIndexOf { h ->
                def norm = normalizeHeader(h)
                norm == "objectid" || norm == "id"
            }

            if (idColumnIndex == -1) {
                print("ERROR: could not find an object ID column in CSV headers: " + headers.join(", "))
            } else {
                println("Using column '${headers[idColumnIndex]}' (index ${idColumnIndex}) as the object ID column")
            }
        } else {
            if (idColumnIndex == -1) {
                // No valid ID column found - skip processing this file entirely
                return
            }

            def values = line.split(",")
            def objectId = values[idColumnIndex]

            def detection = detections.find { it.getID().toString() == objectId }

            // debug
            if (detection == null) {
                print("No match found for object_id: " + objectId)
            }

            // if the object ID is associated with a detection, get pycellmech measurements
            if (detection != null) {
                def ml = detection.getMeasurementList()
                headers.eachWithIndex { header, i ->
                    if (i != idColumnIndex) {
                        try {
                            // show results from pycellmech-generated CSV for all columns except the object ID
                            ml.put(header, Double.parseDouble(values[i]))
                        } catch (NumberFormatException e) {}
                    }
                }
            }
        }
    }
    hierarchy.fireHierarchyChangedEvent(this)
    entry.saveImageData(imageData)
    // clear data so it doesn't clog up QuPath memory
    imageData.getServer().close()
    imageData = null
    System.gc()

    print("Measurements added for: " + imageName)
}