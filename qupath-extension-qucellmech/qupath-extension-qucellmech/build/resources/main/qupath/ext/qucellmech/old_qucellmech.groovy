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

import qupath.lib.objects.PathObject
import qupath.lib.projects.Projects
import qupath.lib.io.GsonTools
import com.google.gson.JsonParser

import qupath.lib.roi.ROIs
import qupath.lib.objects.PathObjects

// first, define the project
def project = getProject()
if (project == null || project.getPath() == null) {
    println("Open a project before running this script!")
    return
}

// ===================================================
// =============== STEP 1: CELLPOSE ==================
// ===================================================

if (runCellpose) {
// Loop through all image entries in the project
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

    // ── Remove small and non-polygon (i.e. full-image rectangle) annotations ──────────────────────────────────
    def minAreaMicrons = 3.0   // adjust this threshold (µm²) as needed

    def toDelete = imgHi.getAnnotationObjects().findAll { ann ->
        def roi      = ann.getROI()
        def tooSmall = roi.getScaledArea(
                        cal.getPixelWidthMicrons(),    // use cal instead of hardcoded values
                        cal.getPixelHeightMicrons()
                    ) < minAreaMicrons
        def notPolygon = !(roi instanceof qupath.lib.roi.PolygonROI)  // proper type check

        return tooSmall || notPolygon    // OR: delete if EITHER condition is true
    }

    println("Removing ${toDelete.size()} annotations (too small or non-polygon)")
    imgHi.removeObjects(toDelete, true)    // true = promote any remaining children rather than deleting them

    // save everything
    imgHi.fireHierarchyChangedEvent(this)
    entry.saveImageData(imageData)
    
    // clear data so it doesn't clog up QuPath memory
    imageData.getServer().close()
    imageData = null
    
    print('Cellpose done! ' + imageName)
    System.gc()
    Thread.sleep(1500)
}
}

// ===================================================
// =========== STEP 2: GEOJSON EXPORT ================
// ===================================================

// gjDir is passed in from QuCellMech user interface:
//   if runCellpose == true, gjDir is PROJECT_BASE_DIR/output/geojson/  (created if needed)
//   elif runCellpose == false, gjDir is a user-selected folder (must already exist)

def gjFolder = new File(gjDir)
if (runCellpose) {
    if (!gjFolder.exists()) {gjFolder.mkdirs()}
} else {
    if (!gjFolder.exists()) {
        print("ERROR: GeoJSON folder does not exist: " + gjDir)
        return
    }
}
        
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
        
// The export GeoJSON loop is only needed if Cellpose ran. Otherwise, the user has
// provided their own GeoJSON, and we skip straight to pycellmech.
if (runCellpose) {
    for (entry in project.getImageList().findAll { selectedImageNames.contains(it.getImageName()) }) {

        // Open each image
        def imageData = entry.readImageData()

        // Get the image name (without the extension)
        def imageName = entry.getImageName().replaceFirst(/\.[^.]+/, "")

        // define geoJSON file path
        def gjPath = buildFilePath(gjDir, imageName + ".geojson")

        // Set the current image data for processing
        imageData.getHierarchy().getSelectionModel().clearSelection()

        // Get the list of cell detections from the image
        def detections = imageData.getHierarchy().getDetectionObjects()
        

        if (!detections.isEmpty()) {
            // Write annotations to GeoJSON
            exportObjectsToGeoJson(detections, gjPath, "FEATURE_COLLECTION")

            print("Exported annotations for: " + imageName + " to " + gjPath)
            // clear data so it doesn't clog up QuPath memory
            imageData.getServer().close()
            imageData = null
            System.gc()
        } else {
            print("No detections found for: " + imageName)
        }
    }
}

// ===================================================================
// ===== STEP 2.5: IF NO CELLPOSE, IMPORT PRE-EXISTING GEOJSON =======
// ===================================================================

if (!runCellpose) {
    for (entry in project.getImageList().findAll { selectedImageNames.contains(it.getImageName()) }) {
        def imageData = entry.readImageData()
        def imageName = entry.getImageName().replaceFirst(/\.[^.]+/, "")
        def hierarchy = imageData.getHierarchy()

        // Search for a GeoJSON file that starts with the image name.
        // This handles naming variations like _nuclei suffix without hardcoding it.
        def gjFile = new File(gjDir).listFiles()?.find {
            it.name.startsWith(imageName) && it.name.endsWith(".geojson")
        }

        if (gjFile == null) {
            print("No GeoJSON found for: " + imageName + " in " + gjDir)
            imageData.getServer().close()
            imageData = null
            continue
        }

        print("Importing GeoJSON for: " + imageName + " from " + gjFile.absolutePath)

        // Clear any existing annotations and import from the GeoJSON file
        hierarchy.clearAll()
        def geojsonText = gjFile.text

        // Parse the FeatureCollection object first
        def jsonObject = JsonParser.parseString(gjFile.text).getAsJsonObject()

        // Then extract the features array from inside it
        def featuresArray = jsonObject.getAsJsonArray("features")

        // Deserialize each individual feature as a PathObject
        def objects = featuresArray.collect { feature ->
            GsonTools.getInstance().fromJson(feature, qupath.lib.objects.PathObject.class)
        }.findAll { it != null }
        
        hierarchy.addObjects(objects)
        hierarchy.fireHierarchyChangedEvent(this)

        entry.saveImageData(imageData)
        imageData.getServer().close()
        imageData = null
        System.gc()

        print("Imported " + objects.size() + " annotations for: " + imageName)
    }
}

// ===================================================
// ============== STEP 3: PYCELLMECH =================
// ===================================================

// Create a temp folder containing only the selected images' GeoJSON files
def tempGjDir = buildFilePath(PROJECT_BASE_DIR, 'output/geojson_temp/')
def tempGjFolder = new File(tempGjDir)
if (!tempGjFolder.exists()) tempGjFolder.mkdirs()

// Copy only the relevant GeoJSON files into the temp folder
for (entry in project.getImageList().findAll { selectedImageNames.contains(it.getImageName()) }) {
    def imageName = entry.getImageName().replaceFirst(/\.[^.]+/, "")
    def gjFile = new File(gjDir).listFiles()?.find {
        it.name.startsWith(imageName) && it.name.endsWith(".geojson")
    }
    if (gjFile != null) {
        def dest = new File(tempGjDir, gjFile.name)
        dest.bytes = gjFile.bytes
        print("Ready for pycellmech: " + gjFile.name)
    } else {
        print("No GeoJSON found for: " + imageName)
    }
}

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


// Finally, we want to use this command to process the geojson files and save them to CSVs
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

    def annotations = imageData.getHierarchy().getAnnotationObjects()
    // define image hierarchy (to be used later when displaying feature calculations)
    def hierarchy = imageData.getHierarchy()
    def headers = null
    
    // debug
    print("Reading CSV: " + csvFile.absolutePath)
    print("Number of annotations found: " + annotations.size())
    
    csvFile.eachLine { line, lineNumber ->
        // first line in CSV is the header, all subsequent lines have an object ID that we can access by index
        if (lineNumber == 1) {
            headers = line.split(",")
            println(headers)
        } else {
            def values = line.split(",")
            def objectId = values[headers.findIndexOf { it == "object_id" }]
            def annotation = annotations.find { it.getID().toString() == objectId }
            
            // debug
            if (annotation == null) {
                print("No match found for object_id: " + objectId)
            }
            
            // if the object ID is associated with an annotation, get pycellmech measurements
            if (annotation != null) {
                def ml = annotation.getMeasurementList()
                headers.eachWithIndex { header, i ->
                    if (header != "object_id") {
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