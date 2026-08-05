"""
File: <polygonal_shape_features.py>

Author: Janan ARSLAN (janan.arslan@icm-institute.org)
Institut du Cerveau - Data Analysis Core (DAC) Platform
Created: 22 FEB 2024
Last Modified: 09 JULY 2026
Modification Author: Janan ARSLAN

"""

#================================================
# BIG THINGS HAPPENING: NO MORE VISUALIZATIONS.
#================================================

import numpy as np
from math import sqrt, pi
import cv2
from sklearn.cluster import KMeans


# ==================================================
# === POLYGONAL FEATURE CALCULATIONS ===============
# ==================================================



def distance_to_segment(point, start, end):
    """
    Calculate the perpendicular distance of a point from a line segment.

    Args:
        point (np.array): The point whose distance from the segment is to be calculated.
        start (np.array): The starting point of the line segment.
        end (np.array): The ending point of the line segment.

    Returns:
        float: The perpendicular distance from the point to the line segment.
    """
    num = np.abs((end[0] - start[0]) * (start[1] - point[1]) - (start[0] - point[0]) * (end[1] - start[1]))
    den = np.sqrt((end[0] - start[0]) ** 2 + (end[1] - start[1]) ** 2)
    return num / den if den != 0 else 0


def distance_threshold_method(contour, threshold):
    """
    Simplify a contour based on a distance threshold method.

    Args:
        contour (list of np.array): The contour to be simplified.
        threshold (float): The maximum allowed distance of any point from its corresponding line segment.

    Returns:
        list of tuple: A list of tuples representing the segments' start and end points.
    """
    segments = []
    start_index = 0

    for i in range(1, len(contour)):
        max_distance = 0
        for j in range(start_index, i + 1):
            distance = distance_to_segment(contour[j], contour[start_index], contour[i])
            if distance > max_distance:
                max_distance = distance
        if max_distance > threshold:
            segments.append((contour[start_index], contour[i - 1]))
            start_index = i - 1

    if not segments or not np.array_equal(segments[-1][1], contour[-1]):
        segments.append((contour[start_index], contour[-1]))

    return segments


def calculate_turn_angle(p1, p2, p3):
    """
    Calculate the turn angle at a point given three points.

    Args:
        p1 (np.array): The first point.
        p2 (np.array): The second point, at which the turn angle is calculated.
        p3 (np.array): The third point.

    Returns:
        float: The turn angle at the second point.
    """
    v1 = p1 - p2
    v2 = p3 - p2
    dot_product = np.dot(v1, v2)

    # need cross product in 2d, which numpy no longer supports (do it manually)
    cross_product = v1[0]*v2[1] - v1[1]*v2[0]

    angle = np.arctan2(cross_product, dot_product)
    return angle



def calculate_significance_measure(L1, L2, total_length):
    """
    Calculate the significance measure of a vertex in a polygon. 

    Args:
        L1 (tuple of np.array): The first line segment (as a pair of points).
        L2 (tuple of np.array): The second line segment (as a pair of points).
        total_length (float): The total length of the polygon's perimeter.

    Returns:
        float: The significance measure of the vertex.
    """
    S_L1 = np.linalg.norm(L1[1] - L1[0])
    S_L2 = np.linalg.norm(L2[1] - L2[0])
    beta = calculate_turn_angle(L1[0], L1[1], L2[1])
    M = (beta * S_L1) / (S_L1 + S_L2) if total_length != 0 else 0
    return M


def polygon_evolution_by_vertex_deletion(contour, threshold):
    """
    Simplify a polygon by iteratively removing vertices based on a significance measure,
    until all remaining vertices have a significance measure above a given threshold.

    Args:
        contour (list of np.array): The vertices of the polygon to be simplified.
        threshold (float): The threshold significance measure below which vertices are removed.

    Returns:
        np.array: The simplified polygon's vertices.
    """
    simplified_contour = contour[:]
    total_length = sum([np.linalg.norm(simplified_contour[i] - simplified_contour[i-1]) 
                        for i in range(1, len(simplified_contour))])

    def M(L1, L2, total_length):
        S_L1 = np.linalg.norm(L1[1] - L1[0])
        S_L2 = np.linalg.norm(L2[1] - L2[0])
        beta = calculate_turn_angle(L1[0], L1[1], L2[1])
        return abs(beta * S_L1) / (S_L1 + S_L2) if total_length else 0

    change = True

    while change:
        change = False
        new_contour = [simplified_contour[0]]
        for i in range(1, len(simplified_contour) - 2):
            L1 = (simplified_contour[i], simplified_contour[i+1])
            L2 = (simplified_contour[i+1], simplified_contour[i+2])
            if M(L1, L2, total_length) < threshold:
                change = True
            else:
                new_contour.append(simplified_contour[i+1])
        new_contour.append(simplified_contour[-1])
        simplified_contour = new_contour

    return np.array(simplified_contour)


def splitting_method(contour, error_tolerance):
    """
    Apply the Splitting Method to simplify a contour.

    Args:
        contour (list of np.array): The contour to be simplified.
        error_tolerance (float): The maximum allowed deviation of points from the line segment.

    Returns:
        list of tuple: A list of tuples representing the segments' start and end indices.
    """
    def recursive_split(points, start, end, tolerance, segments_sm):
        max_distance = 0
        farthest_index = -1
        
        for i in range(start+1, end):
            distance = point_line_distance(points[i], points[start], points[end])
            if distance > max_distance:
                max_distance = distance
                farthest_index = i
        
        if max_distance < tolerance:
            segments_sm.append((start, end))
        else:
            recursive_split(points, start, farthest_index, tolerance, segments_sm)
            recursive_split(points, farthest_index, end, tolerance, segments_sm)
    
    def point_line_distance(point, start, end):
        if np.all(np.equal(start, end)):
            return np.linalg.norm(point - start)
        
        # need cross product in 2d, which numpy no longer supports (do it manually)
        v1 = end - start
        v2 = start - point

        cross = v1[0] * v2[1] - v1[1] * v2[0]

        return np.abs(cross) / np.linalg.norm(v1)


    segments_sm = []
    recursive_split(contour, 0, len(contour)-1, error_tolerance, segments_sm)
    return segments_sm


def calculate_det(p, q, r):
    """
    Calculate the determinant of a 3x3 matrix formed by three points.

    Args:
        p, q, r (np.array): The points used to form the 3x3 matrix.

    Returns:
        float: The determinant of the matrix.
    """
    matrix = np.array([[p[0], p[1], 1],
                       [q[0], q[1], 1],
                       [r[0], r[1], 1]])
    return np.linalg.det(matrix)

def is_convex(p, q, r):
    """
    Determine if the turn from point p to point r via point q is convex.

    Args:
        p, q, r (np.array): The points forming the turn.

    Returns:
        bool: True if the turn is convex, False otherwise.
    """
    return calculate_det(p, q, r) > 0

def label_vertices(contour):
    """
    Label each vertex in a contour as either convex ('W') or concave ('B').

    Args:
        contour (list of np.array): The vertices of the contour.

    Returns:
        list: A list of labels ('W' for convex, 'B' for concave) corresponding to each vertex.
    """

    labels = []
    n = len(contour)
    for i in range(n):
        if is_convex(contour[i-1], contour[i], contour[(i+1) % n]):
            labels.append('W')
        else:
            labels.append('B')
    return labels


def reflect_across_line(point, line_start, line_end):
    """
    Reflect a point across a line defined by two points (line_start, line_end).

    Args:
        point (np.array): The point to be reflected.
        line_start (np.array): The starting point of the line.
        line_end (np.array): The ending point of the line.

    Returns:
        np.array: The reflected point.
    """
    direction = np.array(line_end) - np.array(line_start)
    direction = direction / np.linalg.norm(direction)

    normal = np.array([-direction[1], direction[0]])

    line_to_point = np.array(point) - np.array(line_start)
    t = np.dot(line_to_point, direction)
    closest_point_on_line = np.array(line_start) + t * direction

    point_to_line = closest_point_on_line - point
    reflected_point = point + 2 * point_to_line

    return reflected_point


def find_mirrors(contour, labels):

    """
    For each concave vertex in a contour, find its mirror point across a line defined by 
    its adjacent vertices. The vertices are labeled as convex ('W') or concave ('B').

    Args:
        contour (list of np.array): The vertices of the contour.
        labels (list of str): Labels for each vertex in the contour ('W' or 'B').

    Returns:
        np.array: An array of either original or mirrored points for each vertex.
    """
    
    mirrors = []
    for i, label in enumerate(labels):
        if label == 'B':  
            line_start = contour[i - 1]
            line_end = contour[(i + 1) % len(contour)]

            mirror_point = reflect_across_line(contour[i], line_start, line_end)
            mirrors.append(mirror_point)
        else:
            mirrors.append(contour[i])
    return np.array(mirrors)


def mpp_algorithm(vertices, mirrors, is_convex):
    """
    The Minimum Perimeter Polygon (MPP) algorithm.

    Args:
        vertices (list of np.array): The original vertices of the polygon.
        mirrors (list of np.array): The mirrored points for concave vertices.
        is_convex (list of bool): Indicator of whether each vertex is convex.

    Returns:
        np.array: Vertices of the simplified MPP.
    """

    V0 = vertices[0]
    VL = V0
    WC = BC = V0
    mpp_vertices = [V0]

    for i in range(1, len(vertices)):
        VC = vertices[i]
        if is_convex[i]:
            WC = VC
            candidate_mpp_vertex = WC
        else:
            BC = mirrors[i]
            candidate_mpp_vertex = BC

        if calculate_det(VL, WC, VC) > 0 and calculate_det(VL, BC, VC) <= 0:
            mpp_vertices.append(candidate_mpp_vertex)
            VL = candidate_mpp_vertex

    if not np.array_equal(VL, V0):
        mpp_vertices.append(V0)

    return np.array(mpp_vertices)


def simplify_contour_with_kmeans(contour, num_clusters):
    """
    Simplify a contour using K-means clustering and fit line segments to each cluster.

    Parameters:
    - contour: The contour points as an array of shape (n, 2).
    - num_clusters: The number of clusters to use in K-means.

    Returns:
    - cluster_centers: The centers of the clusters.
    - line_segments: A list of line segments, where each line segment is represented as a tuple (start_point, end_point).
    """
    # Step 1: Remove duplicate points
    points = np.unique(contour, axis=0)
    
    # Step 2: Ensure enough points to cluster meaningfully
    min_points_req = num_clusters

    if len(points) < min_points_req:
        print(f'Skipping annotation: only {len(points)} unique point(s), need at least {min_points_req}')
        return None, None, None
    
    # Step 3: Adjust n_clusters if fewer unique points than requested
    actual_clusters = min(num_clusters, len(points))
    if actual_clusters < num_clusters:
        print(f'Reducing n_clusters from {num_clusters} to {actual_clusters} due to limited unique points')

    kmeans = KMeans(n_clusters=actual_clusters, random_state=0).fit(points)
    labels = kmeans.labels_
    cluster_centers = kmeans.cluster_centers_

    line_segments = []

    for i in range(actual_clusters):
        cluster_points = points[labels == i]
        
        # Step 4: Make sure cluster is not empty before fitLine
        if len(cluster_points) == 0:
            print(f'Skipping empty cluster {i}')
            continue

        [vx, vy, x, y] = cv2.fitLine(cluster_points, cv2.DIST_L2, 0, 0.01, 0.01)

        line_point = np.array([x, y]).reshape(1, 2)
        direction_vector = np.array([vx, vy]).reshape(2, 1)

        dot_product = (cluster_points - line_point) @ direction_vector
        t_values = dot_product / np.sqrt(vx**2 + vy**2)
        t_min, t_max = t_values.min(), t_values.max()

        start_point = (x + t_min * vx, y + t_min * vy)
        end_point = (x + t_max * vx, y + t_max * vy)

        start_point = np.array(start_point).flatten()
        end_point = np.array(end_point).flatten()

        line_segments.append((start_point, end_point))

    return cluster_centers, line_segments, labels

# ==================================================
# === CALL ALL POLYGONAL FEATURES ==================
# ==================================================

def get_polyognal_shape_features(contour):

    # Initialize lists
    dtm = []
    pevd = []
    sm = []
    mpp = []
    km_cc = []
    km_ls = []


    # Simplify the contour array
    contour_array = contour.squeeze()

    x = contour_array[:, 0]
    y = contour_array[:, 1]

    # Calculate gradients
    dx = np.gradient(x)
    dy = np.gradient(y)
    d2x = np.gradient(dx)
    d2y = np.gradient(dy)


    # DTM Calculations
    threshold_segments = 10
    segments = distance_threshold_method(contour_array, threshold_segments)

    # PEVD Calculations
    threshold_vertices = 0.2    

    contour_array_pevd = np.copy(contour_array)
    contour_array_pevd[:, 1] = max(contour_array_pevd[:, 1]) - contour_array_pevd[:, 1]

    pevd_result = polygon_evolution_by_vertex_deletion(contour_array_pevd, threshold_vertices)
    
    # Ensure the y-coordinates are flipped for PEVD result
    pevd_result[:, 1] = max(pevd_result[:, 1]) - pevd_result[:, 1]

    # Calculate SM
    error_tolerance = 0.5
    segments_sm = splitting_method(contour_array, error_tolerance)

    # MPP Calculations
    # Assume labeling of vertices as convex ('W') or concave ('B') is done
    labels = label_vertices(contour_array)

    # Find mirrors of B vertices
    mirrors = find_mirrors(contour_array, labels)

    # Determine convexity as a boolean array
    is_convex_array = np.array([label == 'W' for label in labels])

    mpp_result = mpp_algorithm(contour_array, mirrors, is_convex_array)

    # KMeans Calculations
    # Specify the number of clusters for the K-means algorithm
    num_clusters = 4
    cluster_centers, line_segments, labels = simplify_contour_with_kmeans(contour_array, num_clusters)
    
    poly_features = {
        "dtm": segments,
        "pevd": pevd_result,
        "sm": segments_sm,
        "mpp": mpp_result,
        "km_cc": cluster_centers,
        "km_ls": line_segments,
        "km_labels": labels
        
    }

    return poly_features
