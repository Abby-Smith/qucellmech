'''
======================================================================
 Title:                   PYCELLMECH
 Creating Author:         Janan Arslan
 Creation Date:           22 FEB 2024
 Latest Modification:     28 JULY 2026
 Modification Author:     Abby Smith
 E-mail:                  abigail.smith@icm-institute.org
 Version:                 2.1.13
======================================================================


Pycellmech extracts shape features from images of cells that have been segmented into
"annotations." This can be useful to study relationships between cell morphology and 
the mechanisms or progression of a disease.

The input for this version of Pycellmech is a path to GeoJSON files containing cell annotations.
Features will be extracted for all contours within the image and saved in two CSV files: one 
containing every property calculated by Pycellmech, and another that just contains scalar-value
properties for better readability. To visualize these features, this version of Pycellmech runs within
a QuPath extension that matches annotations (and their individual shape features) with the images they came from.

'''

import os
import pandas as pd
import numpy as np
import argparse
import geojson

from .one_dimensional_features import (
    get_one_dimensional_features
)
from .geometric_shape_features import (
    get_geometric_shape_features
)
from .polygonal_shape_features import (
    get_polyognal_shape_features
)

# Overall process to extract features. file_path should be a GeoJson file
def process_file(gj_path, args):
    all_features = []
    contours = []
    contour_number = 0
    
    try:
        with open(gj_path) as f:
            gj = geojson.load(f)

        for feature in gj['features']:       # all annotations in GeoJSON file
            if feature['geometry']['type'] == "Polygon": # make sure each contour/annotation we are processing is a polygon
                points = feature['geometry']['coordinates'][0] # geojson list of all points in a contour
                contour = np.array(points).squeeze().astype(np.int32) # convert points to numpy array, then squeezes anything in 3 dimensions down to 2
                contours.append(contour)
            else:
                print('not a polygon! ' + str(feature['geometry']['type']))

            cx, cy = np.mean(contour, axis = 0)

            one_d_features = get_one_dimensional_features(contour, cx, cy)
            geom_features = get_geometric_shape_features(contour, cx, cy)
            poly_features = get_polyognal_shape_features(contour)
            label = feature['properties']['classification']['name']  # label from geojson (i.e. nuclei_tumor)
            object_id = feature['id']

            combined_features = {'_name': os.path.basename(gj_path), 'contour_number': contour_number, 'object_id': object_id, 'label': label if label is not None else 'NO LABEL!'}
            combined_features.update({**one_d_features, **geom_features, **poly_features})
            all_features.append(combined_features)
            contour_number += 1

        print(f'Found {str(len(contours))} annotations in GeoJSON file {gj_path}')

    except Exception as e:
        print(f'Error loading GeoJSON file {gj_path}: {e}')

    if len(contours) == 0:
        print(f'No contours found in {gj_path}')
        return

    if all_features:
        features_df = pd.DataFrame(all_features)
        csv_filename = os.path.splitext(os.path.basename(gj_path))[0] + "_features.csv"
        features_df.to_csv(os.path.join(args.csv_save, csv_filename), index=False)

        # OPTIONAL lines 85-95: make a clean csv file that is easier to read, with only scalar values

        clean_df = features_df.set_index('contour_number')
        clean_df.drop(columns = ['_name','EM', 'object_id'], inplace=True)

        # remove "np.numtype" from each entry and show the main (least complicated, scalar) features, rounded to hundredths place
        tmp = clean_df.select_dtypes(include=[np.number])
        clean_df.loc[:, tmp.columns] = np.round(tmp, decimals = 2)
        clean_df = clean_df.loc[:, tmp.columns]

        # make sure labels are still present
        clean_df.insert(0, 'label', features_df['label'])
        clean_df.insert(1, 'object_id', features_df['object_id'])
        print(clean_df)

        cleanpath = str(args.csv_save).rsplit('/', 1)[0] + '/csv_clean'
        os.makedirs(cleanpath, exist_ok= True)
        clean_df.to_csv(os.path.join(cleanpath, csv_filename), index=True)
        
        print(f'[PROCESSED] Features saved to {os.path.join(args.csv_save, csv_filename)}')

def main():
    parser = argparse.ArgumentParser(description='Process binary mask images and extract shape features.')
    parser.add_argument('--geojson_folder', type=str, help='Folder containing GeoJSON files for annotation masks.')
    parser.add_argument('--csv_save', type=str, required=True, help='Folder to save the output CSV files.')

    args = parser.parse_args()

    if os.path.isdir(args.geojson_folder):
        for filename in os.listdir(args.geojson_folder):
            if filename.lower().endswith(".geojson"):
                file_path = os.path.join(args.geojson_folder, filename)
                process_file(file_path, args)
            else:
                print(f'Skipping file: {filename}')
    else:
        print(f'Invalid folder: {args.geojson_folder}')

    if not os.path.exists(args.csv_save):
        os.makedirs(args.csv_save)

if __name__ == "__main__":
    main()
