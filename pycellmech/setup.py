import os
from setuptools import setup, find_packages

current_directory = os.path.abspath(os.path.dirname(__file__))

""" readme_path = os.path.join(current_directory, './README.md')
with open(readme_path, encoding='utf-8') as f:
    long_description = f.read() """

setup(
    name="pycellmech",
    version="2.1.13",
    author=["Janan Arslan", "Abby Smith"],
    author_email="janan.arslan@icm-institute.org",
    description="A shape-based feature extractor for use in biological and medical studies.",
    # long_description=long_description,
    # long_description_content_type="text/markdown",
    url="https://github.com/icm-dac/pycellmech",
    packages=find_packages(where='./src'),
    package_dir={'': './src'},
    classifiers=[
        "Programming Language :: Python :: 3",
        "License :: Other/Proprietary License",
        "Operating System :: OS Independent",
    ],
    python_requires='>=3.6',
    install_requires=[
        "numpy",
        "opencv-python",
        "pandas",
        "scikit-learn",
        "SimpleITK",
        "scikit-image",
        "geojson"
    ],
    include_package_data=True,
    entry_points={
        'console_scripts':
            ['pycellmech=pycellmech.cli:main']
    },
)
