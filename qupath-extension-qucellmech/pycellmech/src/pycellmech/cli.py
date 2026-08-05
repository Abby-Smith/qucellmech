import os
import argparse
from .main import process_file

def main():
    parser = argparse.ArgumentParser(description='Process GeoJSON annotations and extract shape features.')
    parser.add_argument('--geojson_folder', type=str, help='Folder containing GeoJSON files for annotation masks.')
    parser.add_argument('--csv_save', type=str, required=True, help='Folder to save the output CSV files.')

    args = parser.parse_args()
    
    if not os.path.exists(args.csv_save):
        os.makedirs(args.csv_save)

    if os.path.isdir(args.geojson_folder):
        for filename in os.listdir(args.geojson_folder):
            if filename.lower().endswith(".geojson"):
                file_path = os.path.join(args.geojson_folder, filename)
                process_file(file_path, args)
            else:
                print(f'Skipping file: {filename}')
    else:
        print(f'Invalid folder: {args.geojson_folder}')

if __name__ == "__main__":
    main()