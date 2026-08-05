"""
File: <geometric_shape_features.py>

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
from math import cos, sin, sqrt, pi
import cv2


# ==================================================
# === GEOMETRIC FEATURE CALCULATIONS ===============
# ==================================================


def calculate_curvature(dx, dy, d2x, d2y):
    """
    Calculate the curvature of a curve at a point.

    Args:
        dx, dy: First derivatives of the curve in x and y directions.
        d2x, d2y: Second derivatives of the curve in x and y directions.

    Returns:
        float: The curvature value at the given point.
    """

    denominator = (dx**2 + dy**2)**1.5
    if denominator != 0:
        return (dx * d2y - dy * d2x) / denominator
    else:
        return 0


def calculate_triangle_area(p0, p1, c):
    """
    Calculate the area of a triangle given by three points.

    Args:
        p0, p1, c: Points in a 2D plane forming the triangle.

    Returns:
        float: The area of the triangle.
    """

    return 0.5 * abs((p0[0] - c[0]) * (p1[1] - c[1]) - (p1[0] - c[0]) * (p0[1] - c[1]))


def calculate_pq(contour):
    """
    Calculate the sums of squared x-coordinates (p) and product of x and y coordinates (q)
    of a contour.

    Args:
        contour: A 2D array of points defining the contour.

    Returns:
        tuple: The sums p and q.
    """
    if contour.ndim > 2:
        contour = contour.reshape(-1, 2)

    p = np.sum(contour[:, 0]**2)
    q = np.sum(contour[:, 0] * contour[:, 1])
    return p, q



def calculate_AMI(p, q, r, phi):
    """
    Calculate the Average Moment of Inertia (AMI) of a shape.

    Args:
        p, q, r: Precomputed parameters of the shape.
        phi: Angle parameter.

    Returns:
        float: The AMI value.
    """

    return 0.5 * (p + r) - 0.5 * (p - r) * np.cos(2 * phi) - q * np.sin(2 * phi)



def calculate_phi(p, q, r):
    """
    Calculate the angle phi that minimizes the inertia of a shape.

    Args:
        p, q, r: Precomputed parameters of the shape.

    Returns:
        float: The angle phi in radians.
    """

    phi = 0.5 * np.arctan2(q, p - r)
    return phi if phi >= -np.pi / 2 else phi + np.pi



def calculate_ABE(curvature_list):
    """
    Calculate the Average Bending Energy (ABE) of a contour.

    Args:
        curvature_list: List of curvature values along the contour.

    Returns:
        float: The ABE value.
    """
    abe_value = np.mean(np.square(curvature_list))
    return abe_value


def calculate_eccentricity(contour):
    """
    Calculate the eccentricity of a shape based on its contour.

    Args:
        contour: A 2D array of points defining the contour.

    Returns:
        float: The eccentricity of the shape.
    """

    M = cv2.moments(contour)
    mu20 = M['mu20'] / M['m00']
    mu02 = M['mu02'] / M['m00']
    mu11 = M['mu11'] / M['m00']
    
    common = sqrt((mu20 - mu02)**2 + 4*mu11**2)
    lambda1 = 0.5*(mu20 + mu02 + common)
    lambda2 = 0.5*(mu20 + mu02 - common)
    
    eccentricity = sqrt(1 - (lambda2 / lambda1))
    return eccentricity


def calculate_covariance_matrix(contour):
    """
    Calculate the covariance matrix of the points in a contour.

    Args:
        contour: A 2D array of points defining the contour.

    Returns:
        np.array: The covariance matrix.
    """

    if contour.ndim > 2:
        contour = contour.reshape(-1, 2)

    mean_x, mean_y = np.mean(contour, axis=0)

    contour_normalized = contour - [mean_x, mean_y]

    covariance_matrix = np.cov(contour_normalized.T)

    return covariance_matrix


def calculate_mbr_and_elongation(contour):

    """
    Calculate the Minimum Bounding Rectangle (MBR) and elongation of a contour.

    Args:
        contour: A 2D array of points defining the contour.

    Returns:
        tuple: Properties of the MBR including center, dimensions, angle, eccentricity, and elongation.
    """
    
    rect = cv2.minAreaRect(contour)
    (cx, cy), (width, height), angle = rect
    
    if width < height:
        width, height = height, width

    E = height / width
    Elo = 1 - (width / height)
    
    return (cx, cy), (width, height), angle, E, Elo


def calculate_circularity(contour):
    """
    Calculate the circularity of a shape.

    Args:
        contour: A 2D array of points defining the contour.

    Returns:
        tuple: The circularity ratio 
    """

    area = cv2.contourArea(contour)
    perimeter = cv2.arcLength(contour, True)
    
    circle_area = (perimeter ** 2) / (4 * pi)
    circularity_ratio = area / circle_area

    return circularity_ratio


def calculate_ev(contour, covariance_matrix):
    """
    Calculate the Elliptical Variance (EV) of a shape given its contour and covariance matrix.

    Args:
        contour: A 2D array of points defining the contour.
        covariance_matrix: The covariance matrix of the contour points.

    Returns:
        float: The EV value.
    """

    M = len(contour)
    L = contour - contour.mean(axis=0)
    D_prime = np.sqrt(np.sum(L @ np.linalg.inv(covariance_matrix) * L, axis=1))
    mu = D_prime.mean()
    sigma = D_prime.std()
    EV = sigma / mu
    return EV



def calculate_moments(contour):
    """
    Calculate spatial moments up to the second order for a contour.

    Args:
        contour: A 2D array of points defining the contour.

    Returns:
        tuple: Calculated moments.
    """
    moments = cv2.moments(contour)
    m00 = moments['m00']
    m10 = moments['m10']
    m01 = moments['m01']
    m20 = moments['mu20'] / m00
    m02 = moments['mu02'] / m00
    m11 = moments['mu11'] / m00
    return m00, m10, m01, m20, m02, m11



def calculate_em(m00, m20, m02, m11):
    """
    Calculate the Ellipticity Measure (EM) based on moment invariants.

    Args:
        m00, m20, m02, m11: Moments of the contour.

    Returns:
        float: The EM value.
    """

    I = (m20 * m02 - m11**2) / m00**4
    EM = min([16 * pi**2 * I, (16 * pi**2 * I)**-1])
    return EM


def calculate_solidity(contour):
    """
    Calculate the solidity of a shape given its contour.

    Args:
        contour: A 2D array of points defining the contour.

    Returns:
        tuple: Solidity value and the convex hull of the contour.
    """
    
    shape_area = cv2.contourArea(contour)
    
    hull = cv2.convexHull(contour)
    
    hull_area = cv2.contourArea(hull)
    
    if hull_area == 0:
        return 0
    
    solidity = float(shape_area) / hull_area
    return solidity, hull

# ==================================================
# === CALL ALL GEOMETRIC FEATURES ==================
# ==================================================


def get_geometric_shape_features(contour, cx, cy):

    # Initialize lists
    complex_coordinates = []
    cdf = []
    curvature_list = []
    tangent_angles = []
    area_function = []
    clf_values = []
    cog = []


    # Simplify the contour array
    contour_array = contour.squeeze()
    x = contour_array[:, 0]
    y = contour_array[:, 1]

    # Calculate gradients
    dx = np.gradient(x)
    dy = np.gradient(y)
    d2x = np.gradient(dx)
    d2y = np.gradient(dy)

    # Curvature, CDF, and COG calculations 
    for i, point in enumerate(contour_array):
        xi, yi = point
        complex_coordinate = (xi - cx) + 1j * (yi - cy)
        complex_coordinates.append(complex_coordinate)
        cdf.append(np.abs(complex_coordinate))
        curvature_list.append(calculate_curvature(dx[i], dy[i], d2x[i], d2y[i]))
        if i < len(contour_array) - 1:
            area_function.append(calculate_triangle_area(contour_array[i], contour_array[i+1], (cx, cy)))

    
    # Calculate p, q, and r (where r is the sum of y^2)
    p, q = calculate_pq(contour_array - np.array([[cx, cy]]))
    r = np.sum((contour_array[:, 1] - cy)**2)

    # Calculate phi that minimizes AMI
    phi = calculate_phi(p, q, r)

    # AMI Calculations
    I = calculate_AMI(p, q, r, phi)

    # Second derivative check for alpha calculation
    d2I_dphi2 = 2 * (p - r) * np.cos(2 * phi) + 2 * q * np.sin(2 * phi)
    alpha = phi + np.pi/2 if d2I_dphi2 < 0 else phi

    # ABE Calculations
    abe_value = np.mean(curvature_list)

    # Eccentricity Calculations
    eccentricity_value = calculate_eccentricity(contour_array)

    # MBR Calculations
    mbr_center, (mbr_width, mbr_height), mbr_angle, E, Elo = calculate_mbr_and_elongation(contour)

    area = cv2.contourArea(contour)
    perimeter = cv2.arcLength(contour, True)

    # CR Calculations
    circularity_ratio = calculate_circularity(contour)

    m00, m10, m01, m20, m02, m11 = calculate_moments(contour)

    covariance_matrix = np.array([[m20, m11], [m11, m02]])

    # EV Calculations
    ev_value = calculate_ev(contour_array, covariance_matrix)

    # EM Calculations
    em_value = calculate_em(m00, m20, m02, m11)

    # Solidity Calculations
    solidity, hull = calculate_solidity(contour_array) 
    
    geom_features = {
        "abe": abe_value,
        "alpha": alpha,
        "area" : area,
        "eccentricity": eccentricity_value,
        "mbr_center": mbr_center,
        "mbr_width": mbr_width,
        "mbr_height": mbr_height,
        "mbr_angle": mbr_angle,
        "mbr_E": E,
        "elongation": Elo,
        "circularity_ratio": circularity_ratio,
        "EV": ev_value,
        "EM": em_value,
        "hull": hull,
        "perimeter": perimeter,
        "solidity": solidity
        
    }

    return geom_features
