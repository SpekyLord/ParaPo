# ParaPo
**A Mobile-Based Real-Time Jeepney Route Recognition and Navigation Assistance System Using Computer Vision and Graph Algorithms**

**Project Category:** Computer Vision (CV)

**Subject:** Data Structures and Algorithms (DSA)

**Target Platform:** Android Mobile Application

**Initial Coverage:** Iloilo City, Philippines

**Project Status:** Proposed Concept

---

## 1. Project Overview

**ParaPo** is a proposed mobile application designed to help commuters identify the appropriate jeepney routes for their destinations using a combination of **Computer Vision, Optical Character Recognition (OCR), and graph-based route-finding algorithms**.

The application aims to address a common commuting difficulty in the Philippines, particularly among students, tourists, first-time commuters, and individuals unfamiliar with local public transportation routes.

Unlike transportation systems with clearly marked stations and fixed boarding schedules, traditional jeepney commuting often requires passengers to recognize route signboards, understand the areas covered by different jeepneys, and determine whether a particular vehicle will pass their intended destination.

This becomes more challenging in areas where several jeepney routes overlap.

ParaPo proposes a mobile-based solution that combines two main functionalities:

1. **Jeepney Route Finder:** Allows users to enter their current location and destination to identify possible jeepney routes.
2. **Real-Time Jeepney Scanner:** Uses the smartphone's live camera feed to recognize approaching jeepneys and visually indicate whether their identified routes match the user's intended journey.

The application is designed to remain functional even when real-time recognition is unavailable. Users can manually search for routes, explore their paths, and view relevant route information without relying entirely on the camera.

Initially, ParaPo will focus on selected jeepney routes within Iloilo City to maintain a manageable scope for dataset collection, model training, route verification, and system testing.

---

## 2. Problem Statement

Jeepneys are an important form of public transportation in the Philippines. However, identifying the correct jeepney can be confusing, especially for individuals unfamiliar with the routes operating in a particular area.

Several commuting challenges motivate the proposed system.

**1. Multiple overlapping jeepney routes**

A single location may be served by numerous jeepneys traveling toward different destinations. Although several routes may pass through the same area, only some may serve a commuter's intended destination.

**2. Difficulty recognizing route signboards**

Jeepney destination signs may contain small text, multiple place names, or unfamiliar route labels. Approaching vehicles may also be moving, making their signboards difficult to read quickly.

**3. Limited knowledge of route directions**

Recognizing a jeepney's route name does not necessarily mean the vehicle is traveling in the correct direction. Some routes may pass a destination in one direction but not in the other.

**4. Dependence on asking drivers or other commuters**

Passengers often rely on asking people whether a particular jeepney will pass their destination. Although this is helpful, it can be inconvenient for individuals unfamiliar with the area.

**5. Device and connectivity limitations**

Some commuters may have smartphones with lower-quality cameras or limited internet connectivity, making applications that depend entirely on cloud-based image processing less practical.

These challenges highlight the need for a mobile transportation assistant that combines route information with real-time visual recognition.

---

## 3. Proposed Solution

ParaPo aims to provide an integrated commuting assistant through two complementary systems.

### A. Jeepney Route Finder

The Route Finder is the primary navigation feature of the application.

Users enter:

- Their current location or intended boarding area.
- Their desired destination.

Using a database of verified jeepney routes, the application searches for routes that serve the selected origin and destination in the appropriate travel direction.

The system then displays possible jeepney routes, allowing users to explore their paths and identify suitable boarding and drop-off points.

**Example Scenario**

Suppose a student is currently at **Jaro Plaza** and wants to travel to **West Visayas State University (WVSU)**.

The student opens ParaPo and enters:

- **Current Location:** Jaro Plaza
- **Destination:** West Visayas State University

The application searches its stored routes and displays jeepney routes that could serve the journey.

Each route result may include:

- Jeepney route number and name.
- Relevant boarding location.
- Direction of travel.
- Major landmarks and route stops.
- Suggested drop-off location.
- An option to view the complete route on a map.

Only routes verified to serve the requested journey would be presented as direct matches. Other routes could be viewed separately through the Route Explorer.

This allows users to identify potential jeepneys before they even open their camera.

### B. Real-Time Jeepney Scanner

The Real-Time Jeepney Scanner is the application's primary Computer Vision feature.

Instead of requiring users to take a photograph and submit it for analysis, ParaPo will attempt to process a **continuous live camera feed** directly from the smartphone.

When the user points their camera toward an approaching jeepney, the system processes video frames to identify the vehicle or its visible route signage.

The proposed recognition process includes:

1. Capturing frames from the smartphone camera.
2. Detecting jeepneys or their route signboards using a Computer Vision model.
3. Extracting readable route text using OCR.
4. Matching the recognized signage against known jeepney routes.
5. Checking whether the recognized route serves the user's selected destination in the appropriate direction.
6. Displaying the recognition result through a visual overlay on the live camera preview.

The application will use color-coded indicators to communicate its results.

**Green Outline — Matching Route**

Indicates that the recognized jeepney route matches the user's intended journey based on the stored route information and available direction data.

**Red Outline — Non-Matching Route**

Indicates that the recognized route does not directly serve the user's intended journey.

**Gray Outline — Uncertain Recognition**

Indicates that the application cannot confidently identify the route or determine whether the vehicle is traveling in the appropriate direction.

The system should avoid treating an uncertain result as a confirmed route match.

These indicators are intended as commuting assistance rather than guaranteed boarding instructions.

### C. Interactive Jeepney Route Explorer

ParaPo will also allow users to explore the complete route of a selected jeepney.

A user can select a route from the Route Finder or tap a recognized jeepney in the live camera view.

The application will then display a route information page containing:

- Official or locally verified route name and number.
- Starting and ending points.
- Major streets and landmarks.
- Route direction.
- Available boarding and drop-off locations.
- Route visualization on a map.

For example, if a user sees a jeepney passing through Jaro Plaza but does not know where it goes, they can select its recognized route and inspect its path.

This feature can be used independently of the user's planned destination, making ParaPo useful for exploring unfamiliar routes.

---

## 4. Proposed User Workflow

The complete application workflow is designed to be simple and intuitive.

**Step 1: Open ParaPo**

The user launches the application and accesses the main navigation interface.

**Step 2: Enter Origin and Destination**

The user selects their current location and desired destination. Manual location selection will be supported, with GPS-based location detection as an optional enhancement.

**Step 3: Find Available Jeepney Routes**

The application processes the selected locations and searches its route database for compatible jeepney routes.

**Step 4: Explore Recommended Routes**

The user reviews the available options, including route names, major stops, boarding locations, and travel directions.

**Step 5: Activate the Live Camera Scanner**

While waiting for a jeepney in a safe location, the user opens the camera scanner.

The application begins processing the camera feed to detect jeepneys and recognize their route signage.

**Step 6: View Recognition Results**

Recognized routes are compared with the user's selected journey, and a corresponding green, red, or gray outline appears on the camera preview.

**Step 7: Inspect a Detected Jeepney**

The user may select a detected vehicle to view its route information, including its path and major destinations.

**Step 8: Confirm Before Boarding**

The commuter can use the displayed information as a guide while still confirming the route and destination with the driver when necessary.

---

## 5. Technical Implementation

ParaPo combines several technologies to support its primary features.

### 5.1 Computer Vision

Computer Vision is the project's main AI field.

The application will analyze frames captured by the smartphone's live camera to detect jeepneys and identify visible route signage.

Possible object detection models for experimental comparison include:

- **YOLOv8n:** A lightweight object detection model designed for efficient inference.
- **SSD MobileNetV2:** An object detection architecture suitable for resource-constrained devices.
- **EfficientDet-Lite:** An efficient detection model family developed with mobile deployment in mind.

The group will compare these models using the same evaluation dataset and defined detection task.

The selected model will be integrated into the application's recognition pipeline.

### 5.2 Optical Character Recognition (OCR)

OCR will be used to extract text from detected jeepney signboards.

For example, if the camera recognizes a signboard containing a route name, the OCR component attempts to convert the visible letters into machine-readable text.

The extracted text will then be normalized and matched against known route names, aliases, and destination labels.

Because moving vehicles can introduce blur, glare, and partially obstructed text, recognition accuracy will be evaluated under different camera conditions.

The system may combine results from several consecutive video frames to improve recognition stability.

### 5.3 Graph-Based Route Finding

The route-finding system will use graph representations to model jeepney routes and their connections.

In the proposed graph:

- **Vertices (nodes)** represent relevant locations, stops, or boarding points.
- **Edges** represent valid travel connections between locations.
- **Edge direction** represents the direction in which travel is permitted.
- **Edge weights**, where applicable, represent distance or another documented travel cost.

Potential algorithms include:

- **Breadth-First Search (BFS):** For exploring reachable locations or finding paths with the fewest edges in an unweighted graph.
- **Dijkstra's Algorithm:** For calculating shortest paths in a graph with nonnegative edge weights.
- **A* Search:** For finding routes using a suitable distance-based heuristic.

These algorithms may also be compared as an additional DSA experiment if the group decides to expand the routing component.

However, the first version will prioritize identifying verified direct routes before introducing multi-jeepney transfers.

### 5.4 Data Structures

Different data structures will be used throughout the application.

**Graphs and adjacency lists** will represent route connections and support pathfinding.

**Hash maps or dictionaries** will associate detected route identifiers with stored route information.

**Arrays and lists** will manage route sequences, location records, image detections, and model outputs.

**Queues and priority queues** may be used for graph traversal, shortest-path computations, and processing tasks.

These structures will allow the group to demonstrate DSA concepts beyond simply integrating a pretrained AI model.

### 5.5 Mobile Application Development

The proposed implementation will target Android devices.

Possible technologies include:

- **Kotlin:** Android application development.
- **CameraX:** Accessing and processing live camera frames.
- **Python:** Dataset preparation, training, testing, and model comparison.
- **OpenCV:** Image processing and supporting Computer Vision operations.
- **LiteRT / TensorFlow Lite:** Deployment of compatible machine learning models for on-device inference.
- **SQLite:** Local storage of route records, locations, and related information.

The final technology stack will depend on the team's development capacity and the compatibility of the selected recognition model.

The preferred architecture is to perform camera inference locally when feasible, avoiding the need to continuously upload live video to a server.

---

## 6. Dataset Collection and Preparation

Two primary categories of data will be required.

### A. Jeepney Image Dataset

The Computer Vision component will require images of selected Iloilo jeepneys and their route signboards.

The group may collect photographs and short video recordings of jeepneys operating along selected routes, subject to appropriate permissions and privacy considerations.

Images should represent different operating conditions, such as:

- Daytime and nighttime lighting.
- Different viewing angles.
- Moving and stationary vehicles.
- Different distances from the camera.
- Clear and partially obstructed signboards.
- Different route signboard designs.

The dataset will require appropriate annotations for the detection task.

Data preprocessing may include image resizing, normalization, annotation checking, and suitable image augmentation.

Training, validation, and testing datasets should be separated carefully. Images or frames from the same recorded encounter should not be split between training and testing, since this could inflate evaluation results.

### B. Jeepney Route Dataset

The route-finding component will require a structured database of verified jeepney routes.

Each route record should include:

- Route identifier and name.
- Starting and ending locations.
- Ordered route stops or relevant landmarks.
- Directional information.
- Road connections or route geometry.
- Relevant boarding and drop-off points.
- Alternate route names or signboard labels.

Route information should be obtained from appropriate local transportation sources and verified where possible.

The initial dataset will focus only on selected Iloilo City routes rather than attempting to cover the entire province.

---

## 7. Algorithm Selection and Evaluation

In accordance with the DSA project rubric, the group will conduct an actual comparison of three related approaches for the selected Computer Vision task.

The initial proposed comparison involves:

1. YOLOv8n
2. SSD MobileNetV2
3. EfficientDet-Lite

The purpose is to determine which model provides a practical balance between recognition accuracy and computational efficiency for mobile devices.

**Evaluation metrics may include:**

- **Precision:** How often detected objects are correct.
- **Recall:** How many relevant objects the model successfully detects.
- **F1-score:** A combined measure of precision and recall.
- **mAP:** A standard object detection performance metric.
- **Inference Time:** How long the model takes to process a frame.
- **Frame Rate (FPS):** How many video frames can be processed per second.

The complete application will also be evaluated on its ability to correctly identify route text and match recognized routes to the selected destination.

The group will present the comparison results through tables, charts, and demonstrations, then justify the selection of the model used in the final prototype.

---

## 8. Proposed Features and Scope

### Core Features

The first working prototype will prioritize the following:

1. Manual origin and destination selection.
2. Searchable database of selected Iloilo jeepney routes.
3. Display of direct routes that serve the requested journey.
4. Interactive jeepney route information and map view.
5. Live camera-based jeepney or signboard detection.
6. OCR-based route text recognition.
7. Green, red, and gray route-match indicators.
8. Manual route selection when camera recognition fails.
9. A working comparison of three Computer Vision models.

### Potential Future Enhancements

Depending on development progress, additional features may include:

- Multi-jeepney route recommendations and transfers.
- Estimated travel distance and travel time.
- Fare estimation using maintained fare information.
- GPS-assisted boarding and drop-off suggestions.
- Full offline map support.
- Support for additional cities and municipalities.
- Improved low-light and moving-vehicle recognition.
- Voice-assisted commuting guidance.
- Real-time tracking of multiple detected jeepneys.

These enhancements are not required for the initial prototype.

---

## 9. Expected Challenges and Limitations

Although ParaPo presents several interesting engineering opportunities, the group must consider its limitations.

**Recognition Accuracy**

Jeepney signboards may be difficult to recognize because of motion blur, small text, poor lighting, and variations in signboard appearance.

**Availability of Training Data**

A sufficiently diverse local dataset may require considerable time to collect and annotate.

**Route Direction Identification**

Recognizing a route name alone does not confirm the vehicle's direction of travel. The system must use available directional information and return an uncertain result when the direction cannot be established.

**Mobile Device Performance**

Real-time Computer Vision can be computationally demanding, especially on lower-end smartphones. Lightweight models and controlled frame-processing rates will be considered.

**Route Information Accuracy**

Jeepney routes and operating conditions may change over time. The application will depend on the accuracy and maintenance of its stored route information.

**Prototype Coverage**

The first version will support a limited number of verified routes and should not be considered a complete transportation navigation system for all of Iloilo.

---

## 10. Project Objectives

### General Objective

To design and develop a mobile-based jeepney route recognition and navigation assistance system that integrates Computer Vision and graph algorithms to help commuters identify suitable jeepney routes within selected areas of Iloilo City.

### Specific Objectives

1. To develop a mobile interface that allows users to search for jeepney routes based on their current location and destination.

2. To construct a structured database representing selected jeepney routes, stops, and directional connections.

3. To implement a real-time camera-based recognition system capable of detecting jeepney route signage and extracting relevant route information.

4. To evaluate and compare three related Computer Vision models in terms of detection accuracy and computational efficiency.

5. To integrate the selected Computer Vision model with the route-finding system.

6. To apply appropriate data structures and graph algorithms to support route searching and navigation.

7. To implement a visual indication system that distinguishes matching, non-matching, and uncertain route recognition results.

8. To assess the prototype's functionality, performance, limitations, and usefulness in representative commuting scenarios.

---

## 11. Expected Output

The expected final output is a **functional Android application prototype** that demonstrates the integration of Computer Vision and graph-based route finding.

During the project presentation, the group aims to demonstrate the following scenario:

A user launches ParaPo, selects a starting location and destination, and receives a list of potentially suitable jeepney routes.

The user then activates the live camera scanner and points the device toward a jeepney or an appropriate test recording.

The application detects the vehicle's route signage, attempts to recognize its route, and checks whether it matches the user's planned journey.

The result is displayed through a color-coded visual overlay.

The user may then select the detected route to view its complete path and relevant route information.

In addition to the application, the group will present its dataset preparation process, algorithm comparison, evaluation metrics, applied data structures, findings, and identified limitations.

---

## 12. Project Significance and Innovation

ParaPo aims to contribute a practical mobile commuting assistant specifically designed for the Philippine jeepney transportation environment.

Its main contribution is not simply recognizing a jeepney through Computer Vision, but **connecting real-time visual recognition with route-aware navigation and destination matching**.

The proposed system integrates several components:

- Computer Vision for detecting relevant visual information.
- OCR for interpreting jeepney route signage.
- Graph algorithms for analyzing transportation routes.
- Local data storage for maintaining route information.
- Mobile application development for accessible everyday use.

Unlike a basic image-classification application that only identifies an object, ParaPo attempts to translate recognition results into useful commuting information.

The inclusion of a manual route finder ensures that the application remains useful when the camera cannot identify a passing jeepney.

By focusing on selected Iloilo City routes, the group can develop and evaluate a localized proof of concept while maintaining opportunities for future expansion.

**Ultimately, ParaPo aims to help commuters answer two simple questions:**

**"Which jeepney routes can take me to my destination?"**

and

**"Is the jeepney approaching me one of those routes?"**

The proposed project demonstrates how Computer Vision and Data Structures and Algorithms can work together to address a recognizable, real-world transportation problem.
