# Systematic Review - Machine Learning and Mobile Applications for Emotion Recognition in Children with Autism Spectrum Disorder

## Overview

This repository contains all data, scripts, and analysis files for the systematic literature review on machine learning applications and mobile technologies (including serious games) for emotion recognition in childrens with Autism Spectrum Disorder. The research follows PRISMA methodology and analyzes machine learning algorithms, performance metrics, datasets, and mobile/serious game applications for ASD emotion recognition.

## Research Questions

- **RQ1**: Machine learning algorithms, architectures and models for emotion recognition in childrens with ASD
- **RQ2**: Performance evaluation metrics for emotion recognition models
- **RQ3**: Accuracy, precision, recall, F1-score and AUC values
- **RQ4**: Datasets used for model training and validation
- **RQ5**: Facial features and variables employed in recognition systems
- **RQ6**: Limitations in mobile application implementation for resource-limited contexts

## Repository Structure

```
├── data/           CSV exports from academic databases and merged datasets
├── scripts/        R and Python scripts for processing, analysis and visualization
└── results/        Generated figures, tables and statistical outputs
```

## Data Sources

- **Scopus**: Primary academic database
- **Web of Science**: Secondary academic database  
- **IEEE Xplore**: Technical publications database

Total records identified: 188 studies  
Final studies included after PRISMA filtering: 42 studies

## Search String

```
TITLE-ABS-KEY ( ( "autism spectrum disorder" OR "ASD" OR "autism" ) 
OR ( "emotion recognition" OR "facial expression" OR "emotional recognition" ) 
AND ( "machine learning" OR "deep learning" OR "CNN" 
OR "convolutional neural network" OR "artificial intelligence" ) 
AND ( "mobile application" OR "smartphone" OR "serious game" 
OR "digital intervention" ) ) AND ( LIMIT-TO ( OA , "all" ) ) 
AND ( LIMIT-TO ( DOCTYPE , "re" ) OR LIMIT-TO ( DOCTYPE , "cp" ) 
OR LIMIT-TO ( DOCTYPE , "ar" ) )
```

## Methodology

PRISMA systematic literature review methodology applied with inclusion and exclusion criteria based on document type, relevance, access availability, Scimagojr ranking, and contribution to research questions. Studies analyzed from 2010 to October 2025 in computer science domain.

## Technical Requirements

### R Environment

Required packages for data processing, statistical analysis, and preprocessing techniques including K-Fold Cross-Validation and Grid Search optimization.

### Python Environment

Required libraries for data visualization, performance metric calculation, and dataset balancing techniques such as SMOTE and data augmentation.

## Execution Instructions

Scripts should be executed sequentially to ensure proper data flow: first data processing and merging, then statistical analysis applying balancing techniques, and finally visualization generation for metrics and model comparison.

## Key Findings

- **Most utilized models**: Wearable/Mobile (26.19%) and Traditional ML (26.19%)
- **Top metrics**: Accuracy (68.75%), Recall (37.5%), F1-score (37.5%)
- **Primary datasets**: CAFE (7.1%) and FER-2013 (4.8%)
- **Main features**: Facial characteristics (40.48%), CNN architectures (23.81%)
- **Critical limitations**: Reality representativeness (28.57%), imbalanced data (23.81%)

## Authors

- Jhafet Martín Cánepa Maceda
- Christopher Antonio Pillihuamán Santiago

## Institution

Universidad San Ignacio de Loyola - Faculty of Engineering - Information Systems Engineering Program - Lima, Peru

## Contact

jhafet.canepa@usil.pe  
c.pillihuaman@usil.pe

## License

Academic use only
