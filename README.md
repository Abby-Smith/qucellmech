# QuCellMech
A QuPath extension that integrates Cellpose (automated cell segmentation) and Pycellmech (shape feature extraction) into a single pipeline. Designed to analyze geometric properties of cells in TIF images of H&E slides. Built for QuPath 0.7.0 and JDK 25 or newer.

## Prerequesites:
- QuPath project containing images of cells to analyse (TIF works best)
- Either Cellpose installed as QuPath extension (see https://github.com/BIOP/qupath-extension-cellpose), or GeoJSON files containing cell detections for each image
     - Note: if you are importing GeoJSON files, each one must start with the same file name as its TIF counterpart. You will also need a column in the GeoJSON containing a unique object ID for each detection/annotation. If your GeoJSON files do not have these properties, before running the extension you can open them in QuPath and export them in the proper format using ```File > Export objects as GeoJSON```.

## Installation:
#### Step I: Pycellmech
1. Clone this repository into a ```BASE_DIR``` of your choice.
2. Create and activate a virtual environment in the ```BASE_DIR``` where you installed the repository.
3. In the terminal, run ```pip install BASE_DIR/qucellmech/pycellmech.```

#### Step II: QuPath
4. In terminal, change directories to the extension folder and build the extension:  
   ```cd BASE_DIR/qucellmech/qupath-extension-qucellmech```  
   ``` ./gradlew build```
6. In your local file system, locate ```BASE_DIR/qucellmech/qupath-extension-qucellmech/build/libs/qupath-extension-qucellmech-0.1.0.jar```
7. Drag the jar file into an open window of QuPath.
8. Restart QuPath and open a project file (with images inside, ideally TIF). Click ```Extensions > QuCellMech > Run for project```, and customize the prompts to run the pipeline!

## Input:
- Path to Pycellmech executable (```BASE_DIR/YOUR_VENV/bin/pycellmech```)
- If GeoJSON detections already exist (no need to run Cellpose): path to GeoJSON files
- Select images to process

## Output:
- One file per image in each of the following output directories:
  - ```PROJECT_DIRECTORY/output/geojson```: if running Cellpose, generates GeoJSON files of detection coordinates
  - ```PROJECT_DIRECTORY/output/csv```: Raw CSV files of all features processed by Pycellmech
  - ```PROJECT_DIRECTORY/output/csv_clean```: Clean CSV files containing all scalar-value features from Pycellmech
- In QuPath, detection objects (cells) that display PyCellmech scalar-value features when clicked. You may have to close and reopen the image viewer, but not QuPath itself, in order to see the detections after Qucellmech first runs. 

Note: since the full pipeline is computationally expensive (and slow), for slides with many hundreds of cells, it is recommended to run at most 50 images at a time.

-----------------------------------------------------------------------------------------------------------
## Feature description (adapted from original Pycellmech repository):

The current iteration of PyCellMech involves three shape-based feature classes: 
  - One-dimensional function shape features
  - Geometric shape features
  - Polygonal approximation shape features

These feature classes will continue to be built upon in future iterations. A summary of 
these feature descriptions are found below. Features in **bold** are included in the csv_clean files.

### One-Dimensional Function Shape Features: quantifying every point in the cell contour

- ***Centroid***
: The center of an object, i.e. the average x and y coordinates of a cell.


- *Complex Coordinate*
: A complex coordinate function reduces each point in a contour from 2D ```(x,y)``` to 1D ```(x + yi)```. Here, the function calculates ```(x - cx) + i * (y - cy)```, normalizing the points in a cell to the centroid ```(cx, cy)```. The output is a list of complex numbers.


- *Centroid Distance Function (CDF)*
: CDF is the distance from the centroid of an object to a point on its boundary. The output for each cell is a list of numpy floats that describe the shape's geometry relative to its center.


- *Tangent Angles*
: This function finds the angle of the tangent at each point in the contour based on the previous point. Output for each cell is a list of numpy floats between -2pi and 2pi.


- *Curvature*
: This function uses x and y gradients at each point in the contour to calculate curvature, outputting a list of numpy floats for each cell.


- *Area Function (AF)*
: For each point in a contour, AF creates a triangle between that point, the following point, and the centroid, then calculates the area of this triangle. For each cell, the output is a list of numpy floats, representing one area per point.


- *Triangle Area Representation (TAR)*
: TAR calculates the area of the triangle formed by three successive points, for each point in a contour. Outputs a list of numpy floats (one per point) for each cell.


- *Chord Length Function (CLF)*
: CLF measures the distances between pairs of points on the shape's boundary. The output for each cell is a list of numpy floats.

-----------------------------------------------------------------------------------------------------------
### Geometric Shape Features: properties of the whole cell shape

- ***Average Bending Energy (ABE)***
: ABE quantifies the mean amount of effort required to deform the shape. For each cell, ABE is calculated as the average curvature over all points in a contour. Output is a scalar value.


- ***Eccentricity***
: Eccentricity describes the degree to which a shape deviates from being circular. Output is a scalar between 0 (circle) and 1 (most elliptical).


- ***Minimum Bounding Rectangle (MBR)***
: MBR represents the smallest rectangle that entirely encloses the shape, representing its rectilinear approximation. Feature outputs are scalars including rectangle width, height, angle, eccentricity ```(E = height/width)```, and elongation.


- ***Circularity Ratio (CR)***
: CR compares the area of the shape to the area of a circle with the same perimeter/circumference. Output is a scalar for each cell.


- ***Ellipse Variance and Moment Invariants (EM and EV)***
: EV measures the deviation of a shape from an elliptical form, and EM quantifies shape characteristics that remain constant under transformations (e.g., rotation, scaling, and translation).


- ***Solidity***
: Solidity calculates the ratio of the shape's area to the area of its convex hull. The convex hull is like wrapping cell in film: if we draw a line segment between any two points in the cell, that line will still be in the hull. Output for each cell is a scalar between 0 and 1.

-----------------------------------------------------------------------------------------------------------
### Polygonal Approximation Shape Features: simplifying each cell to a polygon

- *Distance Threshold Method (DTM)*
: DTM involves setting a specific distance limit to differentiate between relevant and irrelevant points. DTM simplifies the contour of each cell into longer line segments between fewer points. Output for each cell is a list of two-dimensional arrays, representing the start and end points of each line segment.


- *Polygon Evolution by Vertex Deletion (PEVD)*
: PEVD is a process where vertices are incrementally removed from a polygonal shape to simplify its structure while trying to preserve its overall form and characteristics. Output is a list of two-dimensional arrays, representing the points of the final polygon.


- *Splitting Method (SM)*
: SM involves dividing a shape into smaller, manageable segments or components. Output for each cell is a list of (x,y) endpoints of each small segment.


- *Minimum Perimeter Polygon (MPP)*
: MPP is the polygon with the smallest possible perimeter that can enclose a given shape. This algorithm labels each point as concave or convex, then uses this information to construct a simplified, smaller polygon for each cell. Output is a list of 2D arrays representing the polygon's vertices.


- *K-Means Method*
: KMeans clusters points on the shape into a specified number of groups based on their proximity. Outputs include the following arrays for each cell: KMCC (K-means cluster centers), KMLS (K-means line segments), and labels (the number of the cluster assigned to each vertex in the polygon).
