# HUD — Heads-Up Display

**An Android reference overlay tool built for digital artists.**

HUD is an Android application designed to make visual referencing easier while drawing, illustrating, and working in creative applications.

Inspired by desktop tools such as PureRef and Overlay2, HUD brings the convenience of floating reference windows to Android devices.

## The Idea Behind HUD

HUD started with a problem in my own creative workflow.

When I used a traditional non-display drawing tablet connected to my computer, I relied on desktop reference tools to keep images visible while working in applications such as Clip Studio Paint.

After moving to a Samsung tablet with a built-in display, my workflow changed. I was now drawing directly on the screen, but I couldn't find an Android application that offered the same kind of reference-overlay experience I had become accustomed to on desktop.

After searching for a suitable solution for months, I decided to build one myself.

The goal was straightforward: create a lightweight tool that allows artists to keep visual references accessible without constantly switching between applications or sacrificing valuable screen space.

That idea became HUD.

## Key Features

### Floating Reference Overlays

- Display images and supported video content in floating windows above other applications.
- Keep references visible while switching between apps.
- Move, resize, and zoom the overlay to suit your workspace.
- Adjust overlay opacity to control how prominently the reference appears.

### Lock & Pass-Through Interaction

HUD includes a locking feature that allows the overlay to become non-interactive while remaining visible.

This lets users interact with the drawing application underneath the reference window.

For example, an artist can position an image over a drawing canvas, lock the overlay, and use a stylus to trace or practise drawing without the overlay intercepting their input.

Unlocking the overlay restores normal interaction with the reference window.

### Image Manipulation

HUD includes tools for adjusting reference images without needing to edit the original file:

- Horizontal and vertical flipping.
- Grayscale and colour display modes.
- Opacity adjustment.
- Zoom controls.

### Reference Library & Collections

- Save references within the application.
- Organise images into collections for different projects or subjects.
- Revisit saved references without repeatedly searching for the original files.

### Web Search

HUD includes a web search feature that allows users to find supported images and video content online, bring supported media into the overlay workflow, and save references to the application library.

## Technology & Development

HUD is an independently developed Android application.

Technologies and tools used include:

- **Kotlin** — application development.
- **Android SDK** — Android platform integration.
- **XML** — user interface layouts.
- **Gradle / Kotlin DSL** — project configuration and build management.
- **Git & GitHub** — source control and project management.

The project was developed to solve a practical problem in my own digital-art workflow, with an emphasis on usability and keeping the reference experience accessible while working in other applications.

## Building the Project

The source code is available for portfolio review and non-commercial technical evaluation.

Prospective employers and technical reviewers may clone the repository, open the project in Android Studio, build the application, and run it locally to assess its implementation and functionality.

### Requirements

- Android Studio.
- A compatible Android SDK.
- A physical Android device or emulator.
- Gradle dependencies available for download.

### Getting Started

1. Clone or download this repository.
2. Open the project in Android Studio.
3. Allow Android Studio to complete the Gradle synchronisation.
4. Connect a compatible Android device or start an emulator.
5. Build and run the application from Android Studio.

These instructions are provided exclusively for non-commercial evaluation under the terms of the accompanying proprietary licence.

## Project Motivation

HUD was created because I wanted a tool that supported the way I work as a digital artist.

Rather than constantly switching between applications, I wanted my references to become part of my workspace.

**Your references should work around your creative process, not the other way around.**

## Developer

**T. A. Senna**

Independent Android application developer.

GitHub: https://github.com/TASenna

## Inspiration & Acknowledgements

HUD was inspired by the reference-management and overlay workflows found in desktop applications such as PureRef and Overlay2. HUD is an independent project and is not affiliated with, endorsed by, or associated with either application.

## Licence & Usage

HUD is proprietary software. Copyright and all rights to the original source code and implementation are reserved by the author, except for third-party components governed by their respective licences.

This repository is publicly accessible for portfolio presentation, source-code inspection, and non-commercial employment evaluation.

Prospective employers and technical reviewers are permitted to download, inspect, build, and run the project locally for the purpose of assessing the author's software development skills and suitability for employment.

Commercial use, redistribution, repackaging, publication of the source code, and the creation or distribution of derivative products are not permitted without prior written permission from the copyright holder.

Viewing or accessing this repository does not transfer ownership or grant general permission to reuse the software.

Please see the [LICENSE](LICENSE) file for the full terms and conditions.
